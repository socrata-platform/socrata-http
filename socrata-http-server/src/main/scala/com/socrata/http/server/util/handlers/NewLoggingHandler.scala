package com.socrata.http.server.util.handlers

import com.socrata.http.server.HttpService
import javax.servlet.http.{HttpServletResponseWrapper, HttpServletResponse, HttpServletRequest}
import org.slf4j.{LoggerFactory, Logger}

/**
 * NewLoggingHandler - a handler with standard logging for requests, plus extra features
 * like logging of specific request and response headers.
 * For example, pass in a request ID in the headers, then use this to trace requests through
 * multiple services via logging.
 */
class NewLoggingHandler(underlying: HttpService, options: LoggingOptions) extends HttpService {
  import collection.JavaConverters._

  def apply(req: HttpServletRequest) = { resp =>
    val log = options.log
    val start = System.nanoTime()

    if(log.isInfoEnabled) {
      val reqStr = req.getMethod + " " + req.getRequestURI + Option(req.getQueryString).fold("") { q =>
        "?" + q
      }
      log.info(">>> " + reqStr)
      val headers = options.logRequestHeaders.flatMap { hdr =>
        req.getHeaders(hdr).asScala.map { value => hdr + ": " + value }.toSeq
      }
      if (!headers.isEmpty) log.info(">>> ReqHeaders:: " + headers.mkString(", "))
    }

    val trueResp = new InspectableHttpServletResponse(resp)
    try {
      underlying(req)(trueResp)
    } finally {
      val end = System.nanoTime()
      val extra =
        if(trueResp.status >= 400) " ERROR " + trueResp.status
        else ""
      log.info("<<< {}ms{}", (end - start)/1000000, extra)

      val headers = options.logResponseHeaders.flatMap { hdr =>
        trueResp.getHeaders(hdr).asScala.map { value => hdr + ": " + value }.toSeq
      }
      if (!headers.isEmpty) log.info(">>> RespHeaders:: " + headers.mkString(", "))
    }
  }
}

case class LoggingOptions(log: Logger,
                          logRequestHeaders: Set[String] = Set.empty,
                          logResponseHeaders: Set[String] = Set.empty)

object NewLoggingHandler {
  private val defaultLog = LoggerFactory.getLogger(classOf[NewLoggingHandler])

  val defaultOptions = LoggingOptions(log = defaultLog)

  def apply(options: LoggingOptions = defaultOptions)(service: HttpService): NewLoggingHandler =
    new NewLoggingHandler(service, options)
}
