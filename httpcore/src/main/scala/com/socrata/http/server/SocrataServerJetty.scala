package com.socrata.http.server

import sun.misc.Signal
import sun.misc.SignalHandler

import com.socrata.util.logging.LazyStringLogger
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{Request, Server}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

/**
 * @param handler Service used to handle requests
 * @param onStop Called when a TERM or INT signal is received; should block until it is safe to shut down Jetty.
 */
class SocrataServerJetty(handler: HttpService, onStop: () => Unit, port: Int = 2401, gracefulShutdownTimeoutMS: Int = 5000) {
  val log = LazyStringLogger[this.type]

  /**
   * Runs the servlet container.  Blocks until the container is stopped.
   */
  def run() {
    val server = new Server(port)
    server.setGracefulShutdown(gracefulShutdownTimeoutMS)

    // I don't think this is necessary; it registers the server to be
    // shut down on JVM shutdown, but the only way this should happen
    // gracefully is via JMX or a unix signal, and we're catching
    // those already.  If there are calls to System.exit lurking
    // anywhere.. well, there shouldn't be!
    // server.setStopAtShutdown(true)

    server.setHandler(new AbstractHandler {
      def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        baseRequest.setHandled(true)
        handler(request)(response)
      }
    })

    val mbContainer = new org.eclipse.jetty.jmx.MBeanContainer(java.lang.management.ManagementFactory.getPlatformMBeanServer)
    server.getContainer.addEventListener(mbContainer)
    server.addBean(mbContainer)

    // We want to set up the signal handlers first, but prevent them
    // from telling the server to shut down until after it's fully
    // started up.  Thus the mutex.
    val mutex = new Object
    val SIGTERM = new Signal("TERM")
    val SIGINT = new Signal("INT")
    val oldHandlers = mutex.synchronized {
      val handler = new SignalHandler {
        val firstSignal = new java.util.concurrent.atomic.AtomicBoolean(true)
        def handle(signal: Signal) {
          if(firstSignal.getAndSet(false)) {
            mutex.synchronized {
              Thread.currentThread.setName("Signal handler / jetty stop")
              log.info("Preventing more requests")
              server.getConnectors.foreach(_.close())
              log.info("Calling stop callback")
              onStop()
              log.info("Stopping Jetty")
              server.stop()
            }
          } else {
            log.info("Shutdown already in progress")
          }
        }
      }
      val oldSIGTERM = Signal.handle(SIGTERM, handler)
      val oldSIGINT = Signal.handle(SIGINT, handler)

      server.start()

      (oldSIGTERM, oldSIGINT)
    }

    log.info("Waiting for the server thread to terminate...")
    server.join()
    log.info("Exiting")

    Signal.handle(SIGTERM, oldHandlers._1)
    Signal.handle(SIGINT, oldHandlers._2)
  }
}

