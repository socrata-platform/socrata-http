package com.socrata.http.server

import javax.servlet.http.HttpServletResponse
import java.io.{OutputStream, Writer}
import java.net.URL

import implicits._

object responses {
  def quotedString(s: String) = "\"" + s.replaceAll("\"", "\\\"") + "\""

  def Status(code: Int) = r(_.setStatus(code))
  def Header(name: String, value: String) = r(_.setHeader(name, value))
  def ContentType(mime: String) = r(_.setContentType(mime))

  def Location(url: URL) = Header("Location", url.toExternalForm)

  def Write(f: Writer => Unit) = r { resp => f(resp.getWriter) }
  def Stream(f: OutputStream => Unit) = r { resp => f(resp.getOutputStream) }
  def Content(content: String) = {
    require(content ne null)
    Write { _.write(content) }
  }

  // 2xx
  val OK = Status(HttpServletResponse.SC_OK)
  val Accepted = Status(HttpServletResponse.SC_ACCEPTED)
  val Created = Status(HttpServletResponse.SC_CREATED)
  val NoContent = Status(HttpServletResponse.SC_NO_CONTENT)

  // 3xx
  def MovedTemporarily(url: URL) = Status(HttpServletResponse.SC_MOVED_TEMPORARILY) ~> Location(url)
  def MovedPermanently(url: URL) = Status(HttpServletResponse.SC_MOVED_PERMANENTLY) ~> Location(url)
  def SeeOther(url: URL) = Status(HttpServletResponse.SC_SEE_OTHER) ~> Location(url)
  val NotModified = Status(HttpServletResponse.SC_NOT_MODIFIED)
  def TemporaryRedirect(url: URL) = Status(HttpServletResponse.SC_TEMPORARY_REDIRECT) ~> Location(url)

  // 4xx
  val Unauthorized = Status(HttpServletResponse.SC_UNAUTHORIZED)
  val Forbidden = Status(HttpServletResponse.SC_FORBIDDEN)
  val NotFound = Status(HttpServletResponse.SC_NOT_FOUND)
  val BadRequest = Status(HttpServletResponse.SC_BAD_REQUEST)
  val MethodNotAllowed = Status(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
  val NotAcceptable = Status(HttpServletResponse.SC_NOT_ACCEPTABLE) // Can't fulfil client's request for a content-type
  val Conflict = Status(HttpServletResponse.SC_CONFLICT) // Someone else is making a change at the same time (i.e., lock acquisition failure)
  val PreconditionFailed = Status(HttpServletResponse.SC_PRECONDITION_FAILED)
  val RequestEntityTooLarge = Status(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)
  val UnsupportedMediaType = Status(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE) // upload in a format we don't understand
  val ImATeapot = Status(418) // What?  HttpServletResponse doesn't pre-define this one?

  // 5xx
  val InternalServerError = Status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
  val ServiceUnavailable = Status(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
  val GatewayTimeout = Status(HttpServletResponse.SC_GATEWAY_TIMEOUT)
  val HTTPVersionNotSupported = Status(HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED)

  private def r(mutator: HttpServletResponse => Unit) = mutator
}
