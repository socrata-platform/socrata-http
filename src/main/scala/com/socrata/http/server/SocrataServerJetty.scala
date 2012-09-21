package com.socrata.http.server

import sun.misc.Signal
import sun.misc.SignalHandler

import com.socrata.util.logging.LazyStringLogger
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.server.{Request, Server}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Semaphore

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
  gracefulShutdownTimeoutMS: Int = 60*60*1000
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

    val inProgress = new AtomicInteger(0)

    server.setHandler(new AbstractHandler {
      def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        inProgress.getAndIncrement()
        try {
          baseRequest.setHandled(true)
          handler(request)(response)
        } finally {
          inProgress.getAndDecrement()
        }
      }
    })

    val mbContainer = new org.eclipse.jetty.jmx.MBeanContainer(java.lang.management.ManagementFactory.getPlatformMBeanServer)
    server.getContainer.addEventListener(mbContainer)
    server.addBean(mbContainer)

    val SIGTERM = new Signal("TERM")
    val SIGINT = new Signal("INT")

    // Ick -- but we won't have a cookie until AFTER the server has
    // started, and we won't have started until after hooking the
    // signals.
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
      server.start()

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
        awaitTermination(inProgress)
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

  private def awaitTermination(inProgress: AtomicInteger) {
    val messageEveryMS = 30 * 1000
    val sleepTimeoutMS = 100
    val messageEvery = messageEveryMS / sleepTimeoutMS

    var ctr = 0
    val gracefulShutdownTimeout = gracefulShutdownTimeoutMS * 1000000L
    val start = System.nanoTime()
    var remaining = inProgress.get
    while(remaining != 0 && (System.nanoTime() - start) < gracefulShutdownTimeout) {
      if(ctr % messageEvery == 0) {
        log.info("There are " + remaining + " request(s) still being handled")
      }
      ctr += 1
      Thread.sleep(sleepTimeoutMS)
      remaining = inProgress.get
    }
    if(remaining != 0)
      log.warn("There are " + remaining + " job(s) still running after the shutdown timeout is reached")
    else
      log.info("All requests finished")
  }
}

object SocrataServerJetty {
  private def noop() {}
}
