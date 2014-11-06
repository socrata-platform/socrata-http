package com.socrata.http.server

import java.io.{InputStreamReader, Reader}
import java.util.regex.Pattern

import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.common.util.CharsetFor.UnparsableContentType

import scala.collection.JavaConverters._
import java.net.URLDecoder
import javax.servlet.http.HttpServletRequest

import com.socrata.http.common.util.{ContentNegotiation, HttpUtils, CharsetFor}
import com.socrata.http.server.util.PreconditionParser
import org.joda.time.DateTime

trait HttpRequest {
  def servletRequest: HttpServletRequest
  def resourceScope: ResourceScope
}

class WrapperHttpRequest(val underlying: HttpRequest) extends HttpRequest {
  def servletRequest = underlying.servletRequest
  def resourceScope = underlying.resourceScope
}

object HttpRequest {
  implicit def httpRequestApi(req: HttpRequest) = new HttpRequestApi(req)

  object HttpRequestApi {
    private val QueryStringParameterSeparator = Pattern.compile("[&;]")
  }

  // This will allow us to add more (stateless) methods to HttpRequest without breaking binary compatibility
  final class HttpRequestApi(val `private once 2.10 is no longer a thing`: HttpRequest) extends AnyVal {
    import HttpRequestApi._
    private def self = `private once 2.10 is no longer a thing`
    private def servletRequest = self.servletRequest

    def hostname =
      header("X-Socrata-Host").orElse(header("Host")).getOrElse("").split(':').head

    /** @return `None` if the request path contains malformed percent-encoding; the split and decoded request path otherwise. */
    def requestPath: Option[List[String]] = { // TODO: strip off any context and/or servlet path
      val decodeSegment = { segment: String =>
        try {
          URLDecoder.decode(segment, "UTF-8")
        } catch {
          case _: IllegalArgumentException => // undecodable segment; malformed %-encoding
            return None
        }
      }
      Some(requestPathStr.split("/", -1 /* I hate you, Java */).iterator.drop(1).map(decodeSegment).toList)
    }

    /** @return The undecoded request path as a string */
    def requestPathStr = servletRequest.getRequestURI

    /** @return `None` if there is no query string; the query string otherwise. */
    def queryStr = Option(servletRequest.getQueryString)

    /** @return `None` if the query string has malformed percent-encoding; a list of key-value parameters otherwise. */
    def queryParametersSeq: Option[Seq[(String, Option[String])]] = {
      try {
        val params = queryStr match {
          case Some(q) if q.nonEmpty =>
            QueryStringParameterSeparator.split(q, -1).map { kv =>
              kv.split("=", 2) match {
                case Array(key, value) =>
                  URLDecoder.decode(key, "UTF-8") -> Some(URLDecoder.decode(value, "UTF-8"))
                case Array(key) =>
                  URLDecoder.decode(key, "UTF-8") -> None
              }
            }
          case _ =>
            Array.empty[(String,Option[String])] // Type annotation to work around Scala bug wrt array variance
        }
        Some(params)
      } catch {
        case _ : IllegalArgumentException =>
          None
      }
    }

    /**
      * Precondition: There are no empty or repeated parameters.
      * @return A map of the parameters, or `None` if the query string contains malformed percent-encoding.
      */
    def queryParameters: Option[Map[String,String]] = queryParametersSeq map { s =>
      s.collect {
        case (k, Some(v)) => k -> v
      }(scala.collection.breakOut) // relax inference
    }

    def method: String = servletRequest.getMethod

    def header(name: String): Option[String] =
      Option(servletRequest.getHeader(name))

    def headerNames: Iterator[String] =
      checkHeaderAccess(servletRequest.getHeaderNames).asScala

    def headers(name: String): Iterator[String] =
      checkHeaderAccess(servletRequest.getHeaders(name)).asScala

    def contentType = Option(servletRequest.getContentType)

    def inputStream = servletRequest.getInputStream

    def reader: Either[CharsetFor.ContentTypeFailure, Reader] =
      contentType match {
        case Some(ct) =>
          CharsetFor.contentType(ct) match {
            case CharsetFor.Success(cs) =>
              Right(new InputStreamReader(servletRequest.getInputStream, cs))
            case e: CharsetFor.ContentTypeFailure =>
              Left(e)
          }
        case None =>
          Left(UnparsableContentType("")) // ehhhhhhh
      }

    def accept = headers("Accept").toVector.flatMap(HttpUtils.parseAccept)
    def acceptCharset = headers("Accept-Charset").toVector.flatMap(HttpUtils.parseAcceptCharset)
    def acceptLanguage = headers("Accept-Language").toVector.flatMap(HttpUtils.parseAcceptLanguage)

    def precondition = PreconditionParser.precondition(self)

    def lastModified: Option[DateTime] = dateTimeHeader("Last-Modified")

    def dateTimeHeader(name: String, ignoreParseErrors: Boolean = true): Option[DateTime] = {
      try {
        header(name).map(HttpUtils.parseHttpDate)
      } catch {
        // In most cases, if an incorrectly-formatted date header is set, HTTP 1.1 dictates to simply ignore it
        case ex: IllegalArgumentException if ignoreParseErrors =>
          None
      }
    }

    def negotiateContent(implicit contentNegotiation: ContentNegotiation) = {
      val filename = requestPath.map(_.last).getOrElse("")
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
