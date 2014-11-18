package com.socrata.http.server.util

import javax.servlet.http.HttpServletResponse
import com.socrata.http.server.{HttpRequest, HttpService, HttpResponse}

/** An adapter to catch unexpected errors.  This goes right below the
  * servlet-level interface, which is why it is specifically an HttpService.
  */
abstract class ErrorAdapter(service: HttpService) extends HttpService {
  type Tag

  def apply(req: HttpRequest): HttpResponse = {
    val response = try {
      service(req)
    } catch {
      case e: Exception =>
        return handleError(_, e)
    }

    (resp: HttpServletResponse) => try {
      response(resp)
    } catch {
      case e: Exception =>
        handleError(resp, e)
    }
  }

  private def handleError(resp: HttpServletResponse, ex: Exception) {
    val tag = errorEncountered(ex)
    if(!resp.isCommitted) {
      resp.reset()
      onException(tag)(resp)
    }
  }

  def errorEncountered(ex: Exception): Tag

  def onException(tag: Tag): HttpResponse
}
