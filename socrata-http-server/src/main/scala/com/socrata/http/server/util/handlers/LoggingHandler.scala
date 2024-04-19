package com.socrata.http.server.util.handlers

import com.socrata.http.server.{HttpRequest, HttpService}
import jakarta.servlet.http.{HttpServletResponseWrapper, HttpServletResponse}
import org.slf4j.{LoggerFactory, Logger}

class LoggingHandler(underlying: HttpService, log: Logger = LoggingHandler.defaultLog) extends HttpService {
  def apply(req: HttpRequest) = { resp =>
    val helper = req.resourceScope.open(new LoggingHelper(req, resp))
    underlying(req)(helper.inspectableResp)
  }

  private class LoggingHelper(req: HttpRequest, resp: HttpServletResponse) extends AutoCloseable {
    val start = System.nanoTime()

    val inspectableResp = new InspectableHttpServletResponse(resp)

    if(log.isInfoEnabled) {
      val reqStr = req.method + " " + req.requestPathStr + req.queryStr.fold("") { q =>
        "?" + q
      }
      log.info(">>> " + reqStr)
    }

    override def close() {
      val end = System.nanoTime()
      val extra =
        if(inspectableResp.status >= 400) " ERROR " + inspectableResp.status
        else ""
      log.info("<<< {}ms{}", (end - start)/1000000, extra)
    }
  }
}

class InspectableHttpServletResponse(underlying: HttpServletResponse) extends HttpServletResponseWrapper(underlying) {
  var status = 200
  override def setStatus(x: Int) {
    super.setStatus(x)
    status = x
  }
  @deprecated(message = "prefer setStatus(Int) or sendError(Int,String)", since = "Servlet 2.1")
  override def setStatus(x: Int, m: String) {
    super.setStatus(x, m)
    status = x
  }
  override def sendError(x: Int) {
    super.sendError(x)
    status = x
  }
  override def sendError(x: Int, m: String) {
    super.sendError(x, m)
    status = x
  }
}

object LoggingHandler {
  private val defaultLog = LoggerFactory.getLogger(classOf[LoggingHandler])
  def apply(service: HttpService, log: Logger = defaultLog): LoggingHandler = new LoggingHandler(service)
}
