package com.socrata.http.server

import scala.collection.JavaConverters._
import javax.servlet.http.HttpServletRequest
import java.net.URLDecoder

import com.socrata.http.server.`-impl`.ChainedHttpResponse
import com.socrata.http.common.util.{HttpUtils, ContentNegotiation}
import scala.util.Try
import javax.activation.MimeType
import java.nio.charset.Charset

object implicits {
  implicit def httpResponseToChainedResponse(resp: HttpResponse) = resp match {
    case c: ChainedHttpResponse => c
    case _ => new ChainedHttpResponse(Vector(resp))
  }

  implicit class PimpedOutRequest(val underlying: HttpServletRequest) extends AnyVal {
    def hostname =
      Option(underlying.getHeader("X-Socrata-Host")).getOrElse(
        Option(underlying.getHeader("Host")).getOrElse("")).split(':').head

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

    def accept = headers("Accept").flatMap(HttpUtils.parseAccept)
    def acceptCharset = headers("Accept-Charset").flatMap(HttpUtils.parseAcceptCharset)
    def acceptLanguage = headers("Accept-Language").flatMap(HttpUtils.parseAcceptLanguage)

    /**
     * Negotiate a MIME type for the response.
     * @param available The list of MIME types we're willing to send, from most-preferred to least-preferred.
     */
    def preferredResponseType(available: Iterable[MimeType]): Option[MimeType] = {
      val accept = Try(headers("Accept")).getOrElse(Iterator.empty).toSeq
      ContentNegotiation.mimeType(accept, contentType, Some(underlying.getRequestURI), available)
    }

    /**
     * Negotiate a character encoding for the response.
     * @param available The list of encodings we're willing to produce, from most-preferred to least-preferred.
     */
    def preferredResponseCharset(available: Iterable[Charset]): Option[Charset] = {
      val acceptLanguage = Try(headers("Accept-Charset")).getOrElse(Iterator.empty).toSeq
      ContentNegotiation.charset(acceptLanguage, Some(underlying.getRequestURI), available)
    }

    /**
     * Negotiate a human language for the response.
     * @param available The list of languages we're willing to produce, from most-preferred to least-preferred.
     */
    def preferredLanguage(available: Iterable[String]): Option[String] = {
      val acceptLanguage = Try(headers("Accept-Language")).getOrElse(Iterator.empty).toSeq
      ContentNegotiation.language(acceptLanguage, available)
    }

    private def checkHeaderAccess[T <: AnyRef](result: T): T = {
      if(result eq null) throw new IllegalStateException("Container does not allow access to headers")
      result
    }
  }
}
