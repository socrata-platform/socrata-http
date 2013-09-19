package com.socrata.http.server

import scala.collection.JavaConverters._
import javax.servlet.http.HttpServletRequest
import java.net.URLDecoder

import com.socrata.http.server.`-impl`.ChainedHttpResponse
import com.socrata.http.common.util.{HttpUtils, ContentNegotiation}
import scala.util.Try
import javax.activation.MimeType
import java.nio.charset.Charset
import com.socrata.http.server.util.Precondition

object implicits {
  implicit def httpResponseToChainedResponse(resp: HttpResponse) = resp match {
    case c: ChainedHttpResponse => c
    case _ => new ChainedHttpResponse(Vector(resp))
  }

  implicit class PimpedOutRequest(val underlying: HttpServletRequest) extends AnyVal {
    def hostname =
      header("X-Socrata-Host").orElse(header("Host")).getOrElse("").split(':').head

    def requestPath: List[String] = // TODO: strip off any context and/or servlet path
      underlying.getRequestURI.split("/", -1 /* I hate you, Java */).iterator.drop(1).map(URLDecoder.decode(_, "UTF-8")).toList

    def method: String = underlying.getMethod

    def header(name: String): Option[String] =
      Option(underlying.getHeader(name))

    def headerNames: Iterator[String] =
      checkHeaderAccess(underlying.getHeaderNames).asInstanceOf[java.util.Enumeration[String]].asScala

    def headers(name: String): Iterator[String] =
      checkHeaderAccess(underlying.getHeaders(name)).asInstanceOf[java.util.Enumeration[String]].asScala

    def contentType = Option(underlying.getContentType)

    def accept = headers("Accept").toVector.flatMap(HttpUtils.parseAccept)
    def acceptCharset = headers("Accept-Charset").toVector.flatMap(HttpUtils.parseAcceptCharset)
    def acceptLanguage = headers("Accept-Language").toVector.flatMap(HttpUtils.parseAcceptLanguage)

    def precondition = Precondition.precondition(underlying)

    def negotiateContent(implicit contentNegotiation: ContentNegotiation) = {
      val filename = requestPath.last
      val dotpos = filename.lastIndexOf('.')
      val ext = if(dotpos >= 0) Some(filename.substring(dotpos + 1)) else None
      contentNegotiation(accept.toSeq, contentType, ext, acceptCharset.toSeq, acceptLanguage.toSeq)
    }

    private def checkHeaderAccess[T <: AnyRef](result: T): T = {
      if(result eq null) throw new IllegalStateException("Container does not allow access to headers")
      result
    }
  }
}
