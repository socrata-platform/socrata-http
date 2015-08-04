package com.socrata.http.server

import java.io.InputStreamReader
import java.net.URLDecoder
import java.nio.charset.Charset
import javax.activation.MimeType
import javax.servlet.http.HttpServletRequest

import com.socrata.http.server.`-impl`.ChainedHttpResponse
import com.socrata.http.common.util.{CharsetFor, ContentNegotiation, HttpUtils}
import com.socrata.http.server.util.PreconditionParser
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object implicits { // scalastyle:ignore object.name
  implicit def httpResponseToChainedResponse(resp: HttpResponse): ChainedHttpResponse = resp match {
    case c: ChainedHttpResponse => c
    case _ => new ChainedHttpResponse(Vector(resp))
  }

  /**
   * An extension of the Joda DateTime class with convenience methods related to HTTP.
   * @param underlying Underlying DateTime object.
   */
  implicit class DateTimeWithExtensions(val underlying: DateTime) extends AnyVal {

    /**
     * Renders this DateTime object as an HTTP 1.1 Date String.
     * @return HTTP Date String, ex. "Thu, 08 May 2014 9:36:23 GMT"
     */
    def toHttpDate: String = underlying.toDateTime(DateTimeZone.UTC).toString(HttpUtils.HttpDateFormat)
  }

  // workaround for (sigh) an IDEA bug.  Without this, it won't recognize that subclasses
  // of HttpRequest can use the HttpRequestApi.
  implicit def augmentedHttpRequestSubclass[T <: HttpRequest](x: T): HttpRequest.HttpRequestApi =
    new HttpRequest.HttpRequestApi(x)
}
