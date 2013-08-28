package com.socrata.http.server

import sun.misc.Signal
import sun.misc.SignalHandler
import java.util.concurrent.Semaphore

import com.socrata.util.logging.LazyStringLogger
import org.eclipse.jetty.server.handler.GzipHandler
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.server.{Handler, Server}

case class GzipParameters(excludeUserAgent: String => Boolean = Set.empty,
                          mimeTypes: String => Boolean = Function.const(true),
                          bufferSize: Int = 8192,
                          minGzipSize: Int = 256)

/**
 * @param handler Service used to handle requests.
 * @param onStop Called when a TERM or INT signal is received, after shutting down the listening socket but before
 *               waiting for pending requests to terminate.
 * @param port Port to listen on.  Pass "0" to choose a port at random.
 * @param broker A system to inform (de)readiness.
 * @param deregisterWaitMS Amount of time to give the broker before shutting down the listening socket.
 * @param gracefulShutdownTimeoutMS Maximum amount of time to wait for in-progress requests to stop.
 */
class SocrataServerJetty(
  handler: HttpService,
  onStop: () => Unit = SocrataServerJetty.noop,
  port: Int = 2401,
  broker: ServerBroker = ServerBroker.Noop,
  deregisterWaitMS: Int = 5000,
  gracefulShutdownTimeoutMS: Int = 60*60*1000,
  onFatalException: Throwable => Unit = SocrataServerJetty.shutDownJVM,
  gzipParameters: Option[GzipParameters] = Some(GzipParameters())
) {
  val log = LazyStringLogger[this.type]

  /**
   * Runs the servlet container.  Blocks until the container is stopped.
   */
  def run() {
    val connector = new SelectChannelConnector
    connector.setPort(port)
    val server = new Server
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
    server.getContainer.addEventListener(mbContainer)
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

  private def gzipHandler(underlying: Handler): Handler = gzipParameters match {
    case Some(params) =>
      val gz = new GzipHandler
      gz.setHandler(underlying)
      gz.setExcluded(new StringPredicateSet(params.excludeUserAgent))
      gz.setMimeTypes(new StringPredicateSet(params.mimeTypes))
      gz.setBufferSize(params.bufferSize)
      gz.setMinGzipSize(params.minGzipSize)
      gz
    case None =>
      underlying
  }

  private def awaitTermination(inProgress: () => Int) {
    val messageEveryMS = 30 * 1000
    val sleepTimeoutMS = 100
    val messageEvery = messageEveryMS / sleepTimeoutMS

    var ctr = 0
    val gracefulShutdownTimeout = gracefulShutdownTimeoutMS * 1000000L
    val start = System.nanoTime()
    var remaining = inProgress()
    while(remaining != 0 && (System.nanoTime() - start) < gracefulShutdownTimeout) {
      if(ctr % messageEvery == 0) {
        log.info("There are " + remaining + " request(s) still being handled")
      }
      ctr += 1
      Thread.sleep(sleepTimeoutMS)
      remaining = inProgress()
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
}
