package com.socrata.http.server

import java.io.{InputStreamReader, Reader}
import java.util.regex.Pattern
import javax.activation.MimeType
import javax.servlet.ServletInputStream

import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.common.util.CharsetFor.UnparsableContentType

import scala.collection.JavaConverters._
import java.net.URLDecoder
import javax.servlet.http.{HttpServletRequestWrapper, HttpServletRequest}

import com.socrata.http.common.util.{AliasedCharset, ContentNegotiation, HttpUtils, CharsetFor}
import com.socrata.http.server.util.{Precondition, PreconditionParser}
import org.joda.time.DateTime

trait HttpRequest {
  def servletRequest: HttpRequest.AugmentedHttpServletRequest
  def resourceScope: ResourceScope
}

class ConcreteHttpRequest(val servletRequest: HttpRequest.AugmentedHttpServletRequest,
                          val resourceScope: ResourceScope) extends HttpRequest

class WrapperHttpRequest(val underlying: HttpRequest) extends HttpRequest {
  def servletRequest: HttpRequest.AugmentedHttpServletRequest = underlying.servletRequest
  def resourceScope: ResourceScope = underlying.resourceScope
}

object HttpRequest {
  private val QueryStringParameterSeparator = Pattern.compile("[&;]")

  sealed trait QueryParameter
  case class ParameterValue(value: Option[String]) extends QueryParameter
  case object NoSuchParameter extends QueryParameter

  final class AugmentedHttpServletRequest(val underlying: HttpServletRequest)
    extends HttpServletRequestWrapper(underlying) {
    private[HttpRequest] def requestPathStr = underlying.getRequestURI

    private[HttpRequest] lazy val requestPath: List[String] = // TODO: strip off any context and/or servlet path
      requestPathStr.split("/", -1 /* I hate you, Java */).iterator.drop(1).map(URLDecoder.decode(_, "UTF-8")).toList

    private[HttpRequest] def queryStr = Option(underlying.getQueryString)

    private[HttpRequest] lazy val queryParametersSeq: Seq[(String, Option[String])] =
      queryStr match {
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
          Seq.empty
      }

    private[HttpRequest] lazy val allQueryParameters: Map[String, Seq[Option[String]]] =
      Map.empty[String, Seq[Option[String]]] ++ queryParametersSeq.groupBy(_._1).mapValues(_.map(_._2))

    private[HttpRequest] lazy val queryParameters: Map[String,String] =
      queryParametersSeq.foldLeft(Map.empty[String, String]) { (acc, paramValue) =>
        paramValue match {
          case (k, Some(v)) if !acc.contains(k) =>
            acc + (k -> v)
          case _ =>
            acc
        }
      }
  }

  // This will allow us to add more (stateless) methods to HttpRequest without breaking binary compatibility
  final implicit class HttpRequestApi(val privateOnce210IsNoLongerAThing: HttpRequest) extends AnyVal {
    private def self = privateOnce210IsNoLongerAThing
    private def servletRequest = self.servletRequest

    def hostname: String =
      header("X-Socrata-Host").orElse(header("Host")).getOrElse("").split(':').head

    /** @return The undecoded request path as a string */
    def requestPathStr: String = servletRequest.requestPathStr

    /** @return the split and decoded request path */
    def requestPath: List[String] = servletRequest.requestPath

    /** @return the query string  (i.e., everything after the first "?" in the request's path) */
    def queryStr: Option[String] = servletRequest.queryStr

    /** @return All query parameters, as a sequence of key-value pairs */
    def queryParametersSeq: Seq[(String,Option[String])] = servletRequest.queryParametersSeq

    /**
     * @return A map of the parameters which have (possibly empty) values.  The values are the
     *         first to occur in the query string.
     */
    def queryParameters: Map[String,String] = servletRequest.queryParameters

    /** @return A map of the parameters as a map from parameter names to sequence of values. */
    def allQueryParameters: Map[String,Seq[Option[String]]] = servletRequest.allQueryParameters

    /** @return The first value associated with the given parameter in the query string. */
    def queryParameter(parameter: String): Option[String] =
      servletRequest.queryParameters.get(parameter)

    def method: String = servletRequest.getMethod

    def header(name: String): Option[String] =
      Option(servletRequest.getHeader(name))

    def headerNames: Iterator[String] =
      checkHeaderAccess(servletRequest.getHeaderNames).asScala

    def headers(name: String): Iterator[String] =
      checkHeaderAccess(servletRequest.getHeaders(name)).asScala

    def contentType: Option[String] = Option(servletRequest.getContentType)

    def inputStream: ServletInputStream = servletRequest.getInputStream

    def reader: Either[CharsetFor.ContentTypeFailure, Reader] =
      contentType match {
        case Some(ct) =>
          CharsetFor.contentType(ct) match {
            case CharsetFor.Success(cs) =>
              Right(new InputStreamReader(servletRequest.getInputStream, cs))
            case e: CharsetFor.ContentTypeFailure =>
              Left(e)
          }
        case None => Left(UnparsableContentType("")) // ehhhhhhh
      }

    def accept: Vector[HttpUtils.MediaRange] =
      headers("Accept").toVector.flatMap(HttpUtils.parseAccept)
    def acceptCharset: Vector[HttpUtils.CharsetRange] =
      headers("Accept-Charset").toVector.flatMap(HttpUtils.parseAcceptCharset)
    def acceptLanguage: Vector[HttpUtils.LanguageRange] =
      headers("Accept-Language").toVector.flatMap(HttpUtils.parseAcceptLanguage)

    def precondition: Precondition = PreconditionParser.precondition(self)

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

    def negotiateContent(implicit cn: ContentNegotiation): Option[(MimeType, AliasedCharset, String)] = {
      val filename = requestPath.last
      val dotpos = filename.lastIndexOf('.')
      val ext = if(dotpos >= 0) Some(filename.substring(dotpos + 1)) else None
      cn(accept.toSeq, contentType, ext, acceptCharset.toSeq, acceptLanguage.toSeq)
    }

    private def checkHeaderAccess[T <: AnyRef](result: T): T =
      Option(result).getOrElse(throw new IllegalStateException("Container does not allow access to headers"))
  }
}
