package com.socrata.http.server.util.handlers

import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, responses}
import org.slf4j.LoggerFactory

class ErrorCatcher(underlying: HttpService) extends HttpService {
  import ErrorCatcher.log

  final def apply(req: HttpRequest): HttpResponse = { resp =>
    try {
      underlying(req)(resp)
    } catch {
      case e: Exception =>
        if(resp.isCommitted) {
          log.error("Unhandled exception, but the response is already committed", e)
        } else {
          log.error("Unhandled exception", e)
          resp.reset()
          internalServerError(resp)
        }
      case e: Throwable =>
        // We'll try our best to log a thing but then just re-throw it
        // if the logging succeeds.
        log.error("Unhandled throwable", e)
        throw e
    }
  }

  // Override me if you want a custom internal server error response
  def internalServerError: HttpResponse =
    responses.InternalServerError
}

object ErrorCatcher {
  private val log = LoggerFactory.getLogger(classOf[ErrorCatcher])
  def apply(service: HttpService) = new ErrorCatcher(service)
}
