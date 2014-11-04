package com.socrata.http.server

import javax.servlet.http.HttpServletResponse
import java.io.{OutputStreamWriter, OutputStream, Writer}
import java.net.URL

import com.rojoma.json.v3.ast.JString
import com.rojoma.simplearm.v2._
import com.socrata.http.common.util.CharsetFor
import implicits._
import com.socrata.http.server.util.{EntityTagRenderer, EntityTag}
import org.joda.time.DateTime

object responses {
  def quotedString(s: String) = "\"" + s.replaceAll("\"", "\\\"") + "\""

  def NoOp = r(Function.const(()))

  def Status(code: Int) = r(_.setStatus(code))
  def Header(name: String, value: String) = r(_.setHeader(name, value))
  def ContentType(mime: String) = r(_.setContentType(mime))
  def ContentLength(length: Long) = r(_.setContentLengthLong(length))

  def Location(url: URL) = Header("Location", url.toExternalForm)

  def ETag(etag: EntityTag*) = ETags(etag)

  def ETags(etags: Seq[EntityTag]) = r { resp =>
    etags.foreach { etag =>
      resp.addHeader("ETag", EntityTagRenderer(etag))
    }
  }

  def LastModified(lastModified: DateTime) = Header("Last-Modified", lastModified.toHttpDate)

  def Write(f: Writer => Unit) = r { resp =>
    val contentType = Option(resp.getContentType).orElse(Option(resp.getHeader("content-type"))).getOrElse {
      throw new IllegalStateException("Must set a content-type before writing textual data")
    }
    CharsetFor.contentType(contentType) match {
      case CharsetFor.Success(cs) =>
        using(new OutputStreamWriter(resp.getOutputStream, cs) { override def close() = flush() }) { w =>
          f(w)
        }
      case CharsetFor.UnparsableContentType(ct) =>
        throw new IllegalStateException("Unable to parse the content-type response header " + JString(ct))
      case CharsetFor.UnknownCharset(cs) =>
        throw new IllegalStateException("Unknown charset " + JString(cs))
      case CharsetFor.IllegalCharsetName(cs) =>
        throw new IllegalStateException("Illegal charset name " + JString(cs))
      case CharsetFor.UnknownMimeType(mt) =>
        throw new IllegalStateException("Don't know what charset to use for mime type " + JString(mt.toString) + "; set a charset parameter or register the mime type")
    }
  }

  def Content(content: String) = {
    require(content ne null)
    Write(_.write(content))
  }

  def Stream(f: OutputStream => Unit) = r { resp => f(resp.getOutputStream) }

  def ContentBytes(content: Array[Byte]) = {
    require(content ne null)
    Stream(_.write(content))
  }

  // 2xx
  val OK = Status(HttpServletResponse.SC_OK)
  val Accepted = Status(HttpServletResponse.SC_ACCEPTED)
  val Created = Status(HttpServletResponse.SC_CREATED)
  val NoContent = Status(HttpServletResponse.SC_NO_CONTENT)
  val PartialContent = Status(HttpServletResponse.SC_PARTIAL_CONTENT)

  // 3xx
  val MultipleChoices = Status(HttpServletResponse.SC_MULTIPLE_CHOICES)
  def MovedPermanently(url: URL) = Status(HttpServletResponse.SC_MOVED_PERMANENTLY) ~> Location(url)
  def Found(url: URL) = Status(HttpServletResponse.SC_FOUND) ~> Location(url)
  def MovedTemporarily(url: URL) = Status(HttpServletResponse.SC_MOVED_TEMPORARILY) ~> Location(url)
  def SeeOther(url: URL) = Status(HttpServletResponse.SC_SEE_OTHER) ~> Location(url)
  val NotModified = Status(HttpServletResponse.SC_NOT_MODIFIED)
  def TemporaryRedirect(url: URL) = Status(HttpServletResponse.SC_TEMPORARY_REDIRECT) ~> Location(url)

  // 4xx
  val BadRequest = Status(HttpServletResponse.SC_BAD_REQUEST)
  val Unauthorized = Status(HttpServletResponse.SC_UNAUTHORIZED)
  val PaymentRequired = Status(HttpServletResponse.SC_PAYMENT_REQUIRED)
  val Forbidden = Status(HttpServletResponse.SC_FORBIDDEN)
  val NotFound = Status(HttpServletResponse.SC_NOT_FOUND)
  val MethodNotAllowed = Status(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
  val NotAcceptable = Status(HttpServletResponse.SC_NOT_ACCEPTABLE) // Can't fulfil client's request for a content-type
  val RequestTimeout = Status(HttpServletResponse.SC_REQUEST_TIMEOUT) // Client took too long
  val Conflict = Status(HttpServletResponse.SC_CONFLICT) // Someone else is making a change at the same time (i.e., lock acquisition failure)
  val Gone = Status(HttpServletResponse.SC_GONE) // Like 404 only it used to be here
  val LengthRequired = Status(HttpServletResponse.SC_LENGTH_REQUIRED) // Needs a content-length
  val PreconditionFailed = Status(HttpServletResponse.SC_PRECONDITION_FAILED)
  val RequestEntityTooLarge = Status(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)
  val RequestURITooLong = Status(HttpServletResponse.SC_REQUEST_URI_TOO_LONG)
  val UnsupportedMediaType = Status(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE) // upload in a format we don't understand
  val RequestedRangeNotSatisfiable = Status(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE)
  val ExpectationFailed = Status(HttpServletResponse.SC_EXPECTATION_FAILED)
  val ImATeapot = Status(418) // What?  HttpServletResponse doesn't pre-define this one?
  val PreconditionRequired = Status(428) // RFC 6585
  val TooManyRequests = Status(429) // RFC 6585
  val RequestHeaderFieldsTooLarge = Status(431) // RFC 6585
  val UnavailableForLegalReasons = Status(451)

  // 5xx
  val InternalServerError = Status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
  val NotImplemented = Status(HttpServletResponse.SC_NOT_IMPLEMENTED)
  val BadGateway = Status(HttpServletResponse.SC_BAD_GATEWAY)
  val ServiceUnavailable = Status(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
  val GatewayTimeout = Status(HttpServletResponse.SC_GATEWAY_TIMEOUT)
  val HTTPVersionNotSupported = Status(HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED)
  val VariantAlsoNegotiates = Status(506) // RFC 2295

  private def r(mutator: HttpServletResponse => Unit) = mutator
}
