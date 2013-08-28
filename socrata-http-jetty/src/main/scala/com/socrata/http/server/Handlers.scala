package com.socrata.http.server

import org.eclipse.jetty.server.handler.{HandlerWrapper, AbstractHandler}
import org.eclipse.jetty.server.{Handler, Request}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.util.concurrent.atomic.AtomicInteger

private class FunctionHandler(handler: HttpService) extends AbstractHandler {
  def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
    if(isStarted) {
      baseRequest.setHandled(true)
      handler(request)(response)
    }
  }
}

private class CountingHandler(underlying: Handler, onFatalException: Throwable => Unit) extends HandlerWrapper {
  setHandler(underlying)

  private[this] val log = CountingHandler.log
  private[this] val inProgress = new AtomicInteger(0)

  def currentlyInProgress() = inProgress.get()

  override def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
    if(isStarted) {
      inProgress.getAndIncrement()
      try {
        _handler.handle(target, baseRequest, request, response)
      } catch {
        case e: ThreadDeath =>
          // whoa, someone actually stopped us?  Ok.  We'll obey,
          // but we'll scream first.
          log.warn("Thread terminating due to Thread#stop")
          throw e
        case e: Throwable if nonFatal(e) =>
          log.error("Unhandled exception", e)
          if(!response.isCommitted) response.sendError(500)
          else log.warn("Not sending 500; response already committed")
        case e: Throwable =>
          // ok.  Fatal error.  Out of memory or other really bad
          // "we should be shutting down the JVM now"-level problem.
          onFatalException(e)
      } finally {
        inProgress.getAndDecrement()
      }
    }
  }

  // Note: this has a slightly different meaning for "nonFatal" than
  // scala.util.control.NonFatal's does.  In particular, it's looking
  // for fatal-to-the-Thread, wheras we're looking for fatal-to-the-
  // JVM.
  private def nonFatal(t: Throwable): Boolean = t match {
    case _: StackOverflowError => true
    case _: VirtualMachineError => false
    case _ => true
  }
}

object CountingHandler {
  private val log = org.slf4j.LoggerFactory.getLogger(classOf[CountingHandler])
}
