package com.socrata.http.server.util.handlers

import javax.servlet.http.HttpServletResponse

import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}
import org.slf4j.{Logger, LoggerFactory, MDC}

import scala.collection.JavaConverters._

/**
 * NewLoggingHandler - a handler with standard logging for requests, plus extra features
 * like logging of specific request and response headers.
 * For example, pass in a request ID in the headers, then use this to trace requests through
 * multiple services via logging.
 *
 * Found request headers are added to MDC for logging purposes.  This should be OK since usually
 * very few headers are logged.
 */
class NewLoggingHandler(underlying: HttpService, options: LoggingOptions) extends HttpService {
  def apply(req: HttpRequest): HttpResponse = { resp =>
    val log = options.log
    val start = System.nanoTime()

    if(log.isInfoEnabled) {
      val reqStr = req.method + " " + req.requestPathStr + req.queryStr.fold("") { q =>
        "?" + q
      }
      val headers = options.logRequestHeaders.flatMap { hdr =>
        val values = req.headers(hdr).toSeq
        if (values.nonEmpty) MDC.put(hdr, values.head)
        values.map { value => hdr + ": " + value }
      }
      log.info(">>> " + reqStr)
      if (headers.nonEmpty) log.info(">>> ReqHeaders:: " + headers.mkString(", "))
    }

    val trueResp = new InspectableHttpServletResponse(resp)
    try {
      underlying(req)(trueResp)
    } finally {
      val end = System.nanoTime()
      val extra =
        if (trueResp.status >= HttpServletResponse.SC_BAD_REQUEST) " ERROR " + trueResp.status else ""
      log.info("<<< {}ms{}", (end - start)/1000000, extra)

      val headers = options.logResponseHeaders.flatMap { hdr =>
        trueResp.getHeaders(hdr).asScala.map { value => hdr + ": " + value }.toSeq
      }
      if (headers.nonEmpty) log.info("<<< RespHeaders:: " + headers.mkString(", "))
      MDC.clear()
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
