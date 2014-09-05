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

@deprecated(message="use SocrataServerJetty.Gzip.Options instead", since = "2.1.0")
case class GzipParameters(excludeUserAgent: String => Boolean = Set.empty,
                          excludeMimeTypes: String => Boolean = Set.empty,
                          bufferSize: Int = 8192,
                          minGzipSize: Int = 256)
{
  def toOptions = SocrataServerJetty.Gzip.Options().
    withBufferSize(bufferSize).
    withMinGzipSize(minGzipSize).
    withExcludedMimeTypes(toSet("excludeMimeTypes", excludeMimeTypes)).
    withExcludedUserAgents(toSet("excludeUserAgent", excludeUserAgent))

  private def toSet(name: String, f: String => Boolean): Set[String] = {
    if(f.isInstanceOf[Set[_]]) f.asInstanceOf[Set[String]]
    else {
      val logger = org.slf4j.LoggerFactory.getLogger(classOf[GzipParameters])
      logger.warn("Cannot convert GzipParameters option {} to Set; using the empty set", name)
      Set.empty
    }
  }
}

class SocrataServerJetty(options: SocrataServerJetty.Options) {
  import options._

  /**
   * @param handler Service used to handle requests.
   * @param onStop Called when a TERM or INT signal is received, after shutting down the listening socket but before
   *               waiting for pending requests to terminate.
   * @param port Port to listen on.  Pass "0" to choose a port at random.
   * @param broker A system to inform (de)readiness.
   * @param deregisterWaitMS Amount of time to give the broker before shutting down the listening socket.
   * @param gracefulShutdownTimeoutMS Maximum amount of time to wait for in-progress requests to stop.
   */
  @deprecated("Use SocrataServerJetty.Options instead", since="2.1.0")
  def this(handler: HttpService,
           onStop: () => Unit = SocrataServerJetty.noop,
           port: Int = 2401,
           broker: ServerBroker = ServerBroker.Noop,
           deregisterWaitMS: Int = 5000,
           gracefulShutdownTimeoutMS: Int = 60*60*1000,
           onFatalException: Throwable => Unit = SocrataServerJetty.shutDownJVM,
           gzipParameters: Option[GzipParameters] = Some(GzipParameters())) =
    this(SocrataServerJetty.Options(handler).
           withOnStop(onStop).
           withPort(port).
           withBroker(broker).
           withDeregisterWait(deregisterWaitMS.millis).
           withGracefulShutdownTimeout(gracefulShutdownTimeoutMS.millis).
           withOnFatalException(onFatalException).
           withGzipOptions(gzipParameters.map(_.toOptions)))

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

    val wrappedHandler = List[Handler => Handler](gzipHandler).foldLeft[Handler](new FunctionHandler(handler)) { (h, wrapper) => wrapper(h) }
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
      log.info("Hooking SIGTERM and SIGINT")
      oldSIGTERM = Signal.handle(SIGTERM, signalHandler)
      oldSIGINT = Signal.handle(SIGINT, signalHandler)

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
          signalled.acquire()
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
      log.info("Un-hooking SIGTERM and SIGINT")
      if(oldSIGTERM != null) Signal.handle(SIGTERM, oldSIGTERM)
      if(oldSIGTERM != null) Signal.handle(SIGINT, oldSIGINT)
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

object SocrataServerJetty {
  private def noop() {}

  private def shutDownJVM(ex: Throwable) {
    try {
      // one last gasp at trying to tell the user what happened
      System.err.println("Unhandlable exception " + ex.toString)
    } finally {
      Runtime.getRuntime.halt(127)
    }
  }

  sealed abstract class Options {
    val handler: HttpService

    val onStop: () => Unit
    def withOnStop(callback: () => Unit): Options

    val port: Int
    def withPort(p: Int): Options

    val broker: ServerBroker
    def withBroker(b: ServerBroker): Options

    val deregisterWait: FiniteDuration
    def withDeregisterWait(dw: FiniteDuration): Options

    val gracefulShutdownTimeout: Duration
    def withGracefulShutdownTimeout(gst: Duration): Options

    val onFatalException: Throwable => Unit
    def withOnFatalException(callback: Throwable => Unit): Options

    val gzipOptions: Option[Gzip.Options]
    def withGzipOptions(gzo: Option[Gzip.Options]): Options
  }

  private case class OptionsImpl(
    handler: HttpService,
    onStop: () => Unit = SocrataServerJetty.noop,
    port: Int = 2401,
    broker: ServerBroker = ServerBroker.Noop,
    deregisterWait: FiniteDuration = 5.seconds,
    gracefulShutdownTimeout: Duration = Duration.Inf,
    onFatalException: Throwable => Unit = SocrataServerJetty.shutDownJVM,
    gzipOptions: Option[Gzip.Options] = None
  ) extends Options {
    override def withOnStop(callback: () => Unit) = copy(onStop = callback)
    override def withDeregisterWait(dw: FiniteDuration) = copy(deregisterWait = dw)
    override def withPort(p: Int) = copy(port = p)
    override def withBroker(b: ServerBroker) = copy(broker = b)
    override def withOnFatalException(callback: Throwable => Unit) = copy(onFatalException = callback)
    override def withGracefulShutdownTimeout(gst: Duration) = copy(gracefulShutdownTimeout = gst)
    override def withGzipOptions(gzo: Option[Gzip.Options]): Options = copy(gzipOptions = gzo)
  }

  object Options {
    def apply(handler: HttpService): Options = OptionsImpl(handler)
  }

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
