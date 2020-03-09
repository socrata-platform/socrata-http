package com.socrata.http.server

import scala.util.control.ControlThrowable
import org.eclipse.jetty.server.handler.{HandlerWrapper, AbstractHandler}
import org.eclipse.jetty.server.{Handler, Request}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.util.concurrent.atomic.AtomicInteger
import com.rojoma.simplearm.v2._
import org.slf4j.{MDC, LoggerFactory}

private class FunctionHandler(handler: HttpService) extends AbstractHandler {
  private val log = LoggerFactory.getLogger(classOf[FunctionHandler])

  def handle(target: String, baseRequest: Request, request: HttpServletRequest, baseResponse: HttpServletResponse): Unit = {
    if(isStarted) {
      baseRequest.setHandled(true)
      try {
        MDC.clear()
        using(new ResourceScope("request scope")) { rs =>
          val request = new ConcreteHttpRequest(new HttpRequest.AugmentedHttpServletRequest(baseRequest), rs)
          val response = new ConsumingHttpServletResponse(baseRequest, baseResponse)

          try {
            try { // force checking the path and query parameters
              request.queryParametersSeq
              request.requestPath
            } catch {
              case _: IllegalArgumentException =>
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            handler(request)(response)
          } catch {
            case e: ControlThrowable =>
              throw e
            case e: Exception =>
              log.error("Unhandled exception", e)
              if(!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
              } else {
                log.warn("Response already committed; not sending 500")
              }
            case e: Throwable =>
              try {
                log.error("Unhandled exception", e)
              } catch {
                case e2: Throwable =>
                  e.addSuppressed(e2)
              }
              throw e
          } finally {
            response.consume()
          }
        }
      } finally {
        MDC.clear()
      }
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
