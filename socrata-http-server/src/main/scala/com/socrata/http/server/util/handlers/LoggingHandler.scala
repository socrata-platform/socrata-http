package com.socrata.http.server.util.handlers

import com.socrata.http.server.{HttpResponse, HttpRequest, HttpService}
import javax.servlet.http.{HttpServletResponseWrapper, HttpServletResponse}
import org.slf4j.{LoggerFactory, Logger}

class LoggingHandler(underlying: HttpService, log: Logger = LoggingHandler.defaultLog) extends HttpService {
  def apply(req: HttpRequest): HttpResponse = { resp =>
    val start = System.nanoTime()

    if (log.isInfoEnabled) {
      val reqStr = req.method + " " + req.requestPathStr + Option(req.queryStr).fold("") { q =>
        "?" + q
      }
      log.info(">>> " + reqStr)
    }
    val trueResp = new InspectableHttpServletResponse(resp)
    try {
      underlying(req)(trueResp)
    } finally {
      val end = System.nanoTime()
      val extra =
        if (trueResp.status >= HttpServletResponse.SC_BAD_REQUEST) " ERROR " + trueResp.status else ""
      log.info("<<< {}ms{}", (end - start)/1000000, extra)
    }
  }
}

class InspectableHttpServletResponse(underlying: HttpServletResponse) extends HttpServletResponseWrapper(underlying) {
  var status = HttpServletResponse.SC_OK
  override def setStatus(x: Int): Unit = {
    super.setStatus(x)
    status = x
  }
  @deprecated(message = "prefer setStatus(Int) or sendError(Int,String)", since = "Servlet 2.1")
  override def setStatus(x: Int, m: String): Unit = {
    super.setStatus(x, m)
    status = x
  }
  override def sendError(x: Int): Unit = {
    super.sendError(x)
    status = x
  }
  override def sendError(x: Int, m: String): Unit = {
    super.sendError(x, m)
    status = x
  }
}

object LoggingHandler {
  private val defaultLog = LoggerFactory.getLogger(classOf[LoggingHandler])
  def apply(service: HttpService, log: Logger = defaultLog): LoggingHandler = new LoggingHandler(service)
}
