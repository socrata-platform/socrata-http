package com.socrata.http.server

import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest
import org.eclipse.jetty.server.handler.ErrorHandler

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import sun.misc.Signal
import sun.misc.SignalHandler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.rojoma.simplearm.v2._
import com.socrata.util.logging.LazyStringLogger
import com.typesafe.config.Config
import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.util.thread.QueuedThreadPool

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
    val q = new ArrayBlockingQueue[java.lang.Runnable](options.poolOptions.queueLength)
    val qtp = new QueuedThreadPool(options.poolOptions.maxThreads,
                                   options.poolOptions.minThreads,
                                   options.poolOptions.idleTimeoutMs,
                                   q)
    val server = new Server(qtp)
    val httpConfiguration = new HttpConfiguration
    httpConfiguration.setRequestHeaderSize(options.requestHeaderSize)
    val httpConnectionFactory = new HttpConnectionFactory(httpConfiguration)
    val connector = new ServerConnector(server, httpConnectionFactory)
    connector.setPort(port)
    connector.setIdleTimeout(options.poolOptions.idleTimeoutMs)
    server.addConnector(connector)

    // I don't think this is necessary; it registers the server to be
    // shut down on JVM shutdown, but the only way this should happen
    // gracefully is via JMX or a unix signal, and we're catching
    // those already.  If there are calls to System.exit lurking
    // anywhere.. well, there shouldn't be!
    // server.setStopAtShutdown(true)

    val wrappedHandler = ((gzipHandler _) :: options.extraHandlers).foldLeft[Handler](handler) { (h, wrapper) => wrapper(h) }
    val countingHandler = new CountingHandler(wrappedHandler, onFatalException)
    server.setHandler(countingHandler)

    options.errorHandler.foreach { errorHandler =>
      server.addBean(new ErrorHandler {
        override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {
          using(new ResourceScope("error handler")) { rs =>
            val req = new ConcreteHttpRequest(new AugmentedHttpServletRequest(request), rs)
            baseRequest.setHandled(true)
            errorHandler(req)(response)
          }
        }
      })
    }

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
      val gz = new GzipHandler() {
         override val getVaryField = new org.eclipse.jetty.http.PreEncodedHttpField(org.eclipse.jetty.http.HttpHeader.VARY, opts.varys.mkString(", "))
      }
      gz.setHandler(underlying)
      gz.setExcludedAgentPatterns(opts.excludedUserAgents.toSeq : _*)
      gz.setExcludedMimeTypes(opts.excludedMimeTypes.toSeq : _*)
      gz.setInflateBufferSize(opts.bufferSize)
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

    /** Called when a TERM or INT signal is received, after shutting down the listening socket but before
      * waiting for pending requests to terminate.
      */
    val onStop: () => Unit
    def withOnStop(callback: () => Unit): OptT

    /** Port to listen on.  "0" means to choose a port at random.
      */
    val port: Int
    def withPort(p: Int): OptT

    /** A system to inform of (un)readiness. */
    val broker: ServerBroker
    def withBroker(b: ServerBroker): OptT

    /** Amount of time to give the broker before shutting down the listening socket. */
    val deregisterWait: FiniteDuration
    def withDeregisterWait(dw: FiniteDuration): OptT

    /** Maximum amount of time to wait for in-progress requests to stop. */
    val gracefulShutdownTimeout: Duration
    def withGracefulShutdownTimeout(gst: Duration): OptT

    /** A function to handle fatal exceptions */
    val onFatalException: Throwable => Unit
    def withOnFatalException(callback: Throwable => Unit): OptT

    /** GZIP encoding parameters */
    val gzipOptions: Option[Gzip.Options]
    def withGzipOptions(gzo: Option[Gzip.Options]): OptT

    /** Whether or not SIGTERM and SIGINT are watched to shut down */
    val hookSignals: Boolean
    def withHookSignals(enabled: Boolean): OptT

    /** A list of functions that take a base Jetty Handler and return a wrapped Handler.
      * An example of where this is useful is for instrumenting with MetricsHandler.
      */
    val extraHandlers: List[Handler => Handler]
    def withExtraHandlers(handlers: List[Handler => Handler]): OptT

    val errorHandler: Option[HttpRequest => HttpResponse]
    def withErrorHandler(handler: Option[HttpRequest => HttpResponse]): OptT

    val poolOptions: Pool.Options
    def withPoolOptions(poolOpts: Pool.Options): OptT

    @deprecated("Use pool options", "5/12/2016")
    def withIdleTimeout(it: Int): OptT

    val requestHeaderSize: Int
    def withRequestHeaderSize(size: Int): OptT
  }

  private case class OptionsImpl(
    onStop: () => Unit = noop,
    port: Int = 2401,
    broker: ServerBroker = ServerBroker.Noop,
    deregisterWait: FiniteDuration = 5.seconds,
    gracefulShutdownTimeout: Duration = Duration.Inf,
    onFatalException: Throwable => Unit = shutDownJVM,
    gzipOptions: Option[Gzip.Options] = Some(Gzip.defaultOptions),
    hookSignals: Boolean = true,
    extraHandlers: List[Handler => Handler] = Nil,
    errorHandler: Option[HttpRequest => HttpResponse] = None,
    poolOptions: Pool.Options = Pool.defaultOptions,
    requestHeaderSize: Int = 8192
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
    override def withExtraHandlers(h: List[Handler => Handler]) = copy(extraHandlers = h)
    override def withErrorHandler(h: Option[HttpRequest => HttpResponse]) = copy(errorHandler = h)
    override def withPoolOptions(poolOpt: Pool.Options) = copy(poolOptions = poolOpt)
    override def withIdleTimeout(it: Int) = this
    override def withRequestHeaderSize(size: Int) = copy(requestHeaderSize = size)
  }

  val defaultOptions: Options = OptionsImpl()

  object Gzip {
    sealed abstract class Options {
      val excludedUserAgents: Set[String]
      def withExcludedUserAgents(uas: Set[String]): Options

      /** What items to send in the "Vary" header on compressed responses.
        * GzipHandler by default excludes compressing requests from IE6 when wired up using Jetty's XML config,
        * and so by default varies on User-Agent.  That is bad for caching, and we don't default to excluding
        * IE6, so we default to only varying on Accept-Encoding.  */
      val varys: Set[String]
      def withVarys(vs: Set[String]): Options

      val excludedMimeTypes: Set[String]
      def withExcludedMimeTypes(mts: Set[String]): Options

      val bufferSize: Int
      def withBufferSize(bs: Int): Options

      val minGzipSize: Int
      def withMinGzipSize(s: Int): Options
    }

    private case class OptionsImpl(
      excludedUserAgents: Set[String] = Set.empty,
      varys: Set[String] = Set("Accept-Encoding"),
      excludedMimeTypes: Set[String] = Set.empty,
      bufferSize: Int = 8192,
      minGzipSize: Int = 256,
      requestHeaderSize: Int = 8192
    ) extends Options {
      override def withExcludedUserAgents(uas: Set[String]) = copy(excludedUserAgents = uas)
      override def withVarys(vs: Set[String]) = copy(varys = vs)
      override def withMinGzipSize(s: Int) = copy(minGzipSize = s)
      override def withExcludedMimeTypes(mts: Set[String]) = copy(excludedMimeTypes = mts)
      override def withBufferSize(bs: Int): Options = copy(bufferSize = bs)
    }

    val defaultOptions: Options = OptionsImpl()
  }

  /**
   * Thread pool options.  They ensure that applications have a bounded queue so requests don't
   * pile up forever and make the application unresponsive.  Part of it is choosing the thread
   * pool size smartly as well.... scale it correspnding to what the app can handle.
   * See https://wiki.eclipse.org/Jetty/Howto/High_Load
   *
   * NOTE: To be sure you don't run out of file handles, you need somewhere on the order of
   * <queueLength> + <maxThreads> * 2   file handles.
   */
  object Pool {
    sealed abstract class Options {
      val minThreads: Int
      def withMinThreads(numThreads: Int): Options

      val maxThreads: Int
      def withMaxThreads(numThreads: Int): Options

      val idleTimeoutMs: Int
      def withIdleTimeoutMs(timeout: Int): Options

      // The size of the bounded queue Jetty will use while waiting for an available thread to process
      // a connection.  Anything on the queue already has a connection open.  If the queue is full,
      // then new connections will be rejected.
      // The queue length should be set to <max-requests-per-sec> * <#-secs-to-recover>.  Set too low and
      // requests get rejected too soon.
      val queueLength: Int
      def withQueueLength(queueLength: Int): Options
    }

    private case class OptionsImpl(
      minThreads: Int = 10,
      maxThreads: Int = 100,
      idleTimeoutMs: Int = 30000,
      queueLength: Int = 5000
    ) extends Options {
      override def withMinThreads(numThreads: Int) = copy(minThreads = numThreads)
      override def withMaxThreads(numThreads: Int) = copy(maxThreads = numThreads)
      override def withIdleTimeoutMs(timeout: Int) = copy(idleTimeoutMs = timeout)
      override def withQueueLength(ql: Int) = copy(queueLength = ql)
    }

    val defaultOptions: Options = OptionsImpl()

    def apply(config: Config): Options = {
      OptionsImpl(config.getInt("min-threads"),
                  config.getInt("max-threads"),
                  config.getMilliseconds("idle-timeout").toInt,
                  config.getInt("queue-length"))
    }
  }
}
