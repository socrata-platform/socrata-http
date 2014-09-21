package com.socrata.http.server

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import sun.misc.Signal
import sun.misc.SignalHandler
import java.util.concurrent.Semaphore

import com.socrata.util.logging.LazyStringLogger
import org.eclipse.jetty.server.{ServerConnector, Handler, Server}
import org.eclipse.jetty.servlets.gzip.GzipHandler
import org.eclipse.jetty.util.component.LifeCycle

/**
 * Base class for Socrata HTTP Servers.  Manages server lifecycle, including graceful
 * termination waiting for requests to clear;  service discovery registration;  signal
 * handling;  and more.
 *
 */
abstract class AbstractSocrataServerJetty(handler: Handler, options: AbstractSocrataServerJetty.Options = AbstractSocrataServerJetty.defaultOptions) {
  import options._
  import AbstractSocrataServerJetty._

  val log = LazyStringLogger[this.type]

  /**
   * Runs the servlet container.  Blocks until the container is stopped.
   */
  def run() {
    val deregisterWaitMS = deregisterWait.toMillis.min(Long.MaxValue).max(0L).toInt
    val server = new Server
    val connector = new ServerConnector(server)
    connector.setPort(port)
    server.addConnector(connector)

    // I don't think this is necessary; it registers the server to be
    // shut down on JVM shutdown, but the only way this should happen
    // gracefully is via JMX or a unix signal, and we're catching
    // those already.  If there are calls to System.exit lurking
    // anywhere.. well, there shouldn't be!
    // server.setStopAtShutdown(true)

    val wrappedHandler = List[Handler => Handler](gzipHandler).foldLeft[Handler](handler) { (h, wrapper) => wrapper(h) }
    val countingHandler = new CountingHandler(wrappedHandler, onFatalException)
    server.setHandler(countingHandler)

    val mbContainer = new org.eclipse.jetty.jmx.MBeanContainer(java.lang.management.ManagementFactory.getPlatformMBeanServer)
    server.addBean(mbContainer)

    val SIGTERM = new Signal("TERM")
    val SIGINT = new Signal("INT")

    val signalled = new Semaphore(0)

    val signalHandler = new SignalHandler {
      val firstSignal = new java.util.concurrent.atomic.AtomicBoolean(true)
      def handle(signal: Signal) {
        if(firstSignal.getAndSet(false)) {
          log.info("Signalling main thread to wake up")
          signalled.release()
        } else {
          log.info("Shutdown already in progress")
        }
      }
    }

    server.addLifeCycleListener(new LifeCycle.Listener {
      def lifeCycleStarting(event: LifeCycle) {}

      def lifeCycleStarted(event: LifeCycle) {}

      def lifeCycleFailure(event: LifeCycle, cause: Throwable) {}

      def lifeCycleStopping(event: LifeCycle) {}

      def lifeCycleStopped(event: LifeCycle) { signalled.release() }
    })

    var oldSIGTERM: SignalHandler = null
    var oldSIGINT: SignalHandler = null
    try {
      if(hookSignals) {
        log.info("Hooking SIGTERM and SIGINT")
        oldSIGTERM = Signal.handle(SIGTERM, signalHandler)
        oldSIGINT = Signal.handle(SIGINT, signalHandler)
      }

      log.info("Starting server")
      try {
        server.start()
      } catch {
        case e: Exception =>
          server.stop()
          server.join()
          throw e
      }

      try {
        log.info("Registering with broker")
        val cookie = broker.register(connector.getLocalPort)

        try {
          log.info("Going to sleep...")
          try {
            signalled.acquire()
          } catch {
            case e: InterruptedException => // ok, we're awake
          }
          log.info("Awakened!")
        } finally {
          log.info("De-registering with broker")
          broker.deregister(cookie)
        }

        log.info("Waiting to give any final brokered requests time to arrive")
        Thread.sleep(deregisterWaitMS)

        log.info("Preventing more requests")
        connector.close()

        log.info("Calling stop callback")
        onStop()

        // It is theoretically possible that a connection will have been
        // made _just_ before closing the connector but that it has not yet
        // gotten to the point of incrementing inProgress here.  The
        // deregisterWaitMS timeout should be large enough to prevent this
        // from happening.
        log.info("Waiting for all pending requests to terminate")
        awaitTermination(countingHandler.currentlyInProgress)
      } finally {
        log.info("Stopping Jetty")
        server.stop()
      }

      log.info("Waiting for the server thread to terminate...")
      server.join()
    } finally {
      if(hookSignals) {
        log.info("Un-hooking SIGTERM and SIGINT")
        if(oldSIGTERM != null) Signal.handle(SIGTERM, oldSIGTERM)
        if(oldSIGTERM != null) Signal.handle(SIGINT, oldSIGINT)
      }
    }

    log.info("Exiting")
  }

  private def gzipHandler(underlying: Handler): Handler = gzipOptions match {
    case Some(opts) =>
      val gz = new GzipHandler
      gz.setHandler(underlying)
      gz.setExcluded(opts.excludedUserAgents.asJava)
      gz.setMimeTypes(opts.excludedMimeTypes.asJava)
      gz.setExcludeMimeTypes(true)
      gz.setBufferSize(opts.bufferSize)
      gz.setMinGzipSize(opts.minGzipSize)
      gz
    case None =>
      underlying
  }

  private def awaitTermination(inProgress: () => Int) {
    val messageEveryMS = 30 * 1000
    val sleepTimeoutMS = 100
    val messageEvery = messageEveryMS / sleepTimeoutMS
    var remaining = inProgress()

    gracefulShutdownTimeout match {
      case finite: FiniteDuration =>
        var ctr = 0
        val gracefulShutdownTimeoutMS = finite.toMillis
        val start = System.nanoTime()
        while(remaining != 0 && ((System.nanoTime() - start) / 1000000L) < gracefulShutdownTimeoutMS) {
          if(ctr % messageEvery == 0) {
            log.info("There are " + remaining + " request(s) still being handled")
          }
          ctr += 1
          Thread.sleep(sleepTimeoutMS)
          remaining = inProgress()
        }
      case infinite: Duration.Infinite =>
        if(infinite == Duration.Inf) {
          var ctr = 0
          while(remaining != 0) {
            if(ctr % messageEvery == 0) {
              log.info("There are " + remaining + " request(s) still being handled")
            }
            ctr += 1
            Thread.sleep(sleepTimeoutMS)
            remaining = inProgress()
          }
        } else {
          // negative-infinite == stop immediately
        }
    }

    if(remaining != 0)
      log.warn("There are " + remaining + " job(s) still running after the shutdown timeout is reached")
    else
      log.info("All requests finished")
  }
}

object AbstractSocrataServerJetty {
  private[server] def noop() {}

  private[server] def shutDownJVM(ex: Throwable) {
    try {
      // one last gasp at trying to tell the user what happened
      System.err.println("Unhandlable exception " + ex.toString)
    } finally {
      Runtime.getRuntime.halt(127)
    }
  }

  abstract class Options {
    type OptT <: Options

    val onStop: () => Unit
    def withOnStop(callback: () => Unit): OptT

    val port: Int
    def withPort(p: Int): OptT

    val broker: ServerBroker
    def withBroker(b: ServerBroker): OptT

    val deregisterWait: FiniteDuration
    def withDeregisterWait(dw: FiniteDuration): OptT

    val gracefulShutdownTimeout: Duration
    def withGracefulShutdownTimeout(gst: Duration): OptT

    val onFatalException: Throwable => Unit
    def withOnFatalException(callback: Throwable => Unit): OptT

    val gzipOptions: Option[Gzip.Options]
    def withGzipOptions(gzo: Option[Gzip.Options]): OptT

    val hookSignals: Boolean
    def withHookSignals(enabled: Boolean): OptT
  }

  private case class OptionsImpl(
    onStop: () => Unit = noop,
    port: Int = 2401,
    broker: ServerBroker = ServerBroker.Noop,
    deregisterWait: FiniteDuration = 5.seconds,
    gracefulShutdownTimeout: Duration = Duration.Inf,
    onFatalException: Throwable => Unit = shutDownJVM,
    gzipOptions: Option[Gzip.Options] = None,
    hookSignals: Boolean = true
  ) extends Options {
    type OptT = OptionsImpl

    override def withOnStop(callback: () => Unit) = copy(onStop = callback)
    override def withDeregisterWait(dw: FiniteDuration) = copy(deregisterWait = dw)
    override def withPort(p: Int) = copy(port = p)
    override def withBroker(b: ServerBroker) = copy(broker = b)
    override def withOnFatalException(callback: Throwable => Unit) = copy(onFatalException = callback)
    override def withGracefulShutdownTimeout(gst: Duration) = copy(gracefulShutdownTimeout = gst)
    override def withGzipOptions(gzo: Option[Gzip.Options]) = copy(gzipOptions = gzo)
    override def withHookSignals(enabled: Boolean) = copy(hookSignals = enabled)
  }

  val defaultOptions: Options = OptionsImpl()

  object Gzip {
    sealed abstract class Options {
      val excludedUserAgents: Set[String]
      def withExcludedUserAgents(uas: Set[String]): Options

      val excludedMimeTypes: Set[String]
      def withExcludedMimeTypes(mts: Set[String]): Options

      val bufferSize: Int
      def withBufferSize(bs: Int): Options

      val minGzipSize: Int
      def withMinGzipSize(s: Int): Options
    }

    private case class OptionsImpl(
      excludedUserAgents: Set[String] = Set.empty,
      excludedMimeTypes: Set[String] = Set.empty,
      bufferSize: Int = 8192,
      minGzipSize: Int = 256
    ) extends Options {
      override def withExcludedUserAgents(uas: Set[String]) = copy(excludedUserAgents = uas)
      override def withMinGzipSize(s: Int) = copy(minGzipSize = s)
      override def withExcludedMimeTypes(mts: Set[String]) = copy(excludedMimeTypes = mts)
      override def withBufferSize(bs: Int): Options = copy(bufferSize = bs)
    }

    object Options {
      def apply(): Options = OptionsImpl()
    }
  }
}
