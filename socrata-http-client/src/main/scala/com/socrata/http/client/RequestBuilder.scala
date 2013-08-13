package com.socrata.http.client

import java.io.InputStream

import com.rojoma.json.io.JsonEvent
import com.socrata.http.client.util.HttpUtils
import com.socrata.http.common.livenesscheck.LivenessCheckInfo

class RequestBuilder private (val host: String,
                              val secure: Boolean,
                              val port: Int,
                              val path: Iterable[String],
                              val query: Iterable[(String, String)],
                              val headers: Iterable[(String, String)],
                              val method: Option[String],
                              val connectTimeoutMS: Option[Int],
                              val receiveTimeoutMS: Option[Int],
                              val timeoutMS: Option[Int],
                              val livenessCheckInfo: Option[LivenessCheckInfo]) {
  def copy(host: String = this.host,
           secure: Boolean = this.secure,
           port: Int = this.port,
           path: Iterable[String] = this.path,
           query: Iterable[(String, String)] = this.query,
           headers: Iterable[(String, String)] = this.headers,
           method: Option[String] = this.method,
           connectTimeoutMS: Option[Int] = this.connectTimeoutMS,
           receiveTimeoutMS: Option[Int] = this.receiveTimeoutMS,
           timeoutMS: Option[Int] = this.timeoutMS,
           livenessCheckInfo: Option[LivenessCheckInfo] = this.livenessCheckInfo) =
    new RequestBuilder(host, secure, port, path, query, headers, method, connectTimeoutMS, receiveTimeoutMS, timeoutMS, livenessCheckInfo)

  def port(newPort: Int) = copy(port = newPort)

  def path(newPath: Seq[String]) = copy(path = newPath)

  def p(newPath: String*) = copy(path = newPath)

  /** Sets the query parameters for this request. */
  def query(newQuery: Iterable[(String, String)]) = copy(query = newQuery)

  /** Sets the query parameters for this request.  Equivalent to `query(Seq(...))`. */
  def q(newQuery: (String, String)*) = query(newQuery)

  def addParameter(parameter: (String, String)) = copy(query = query.toVector :+ parameter)

  /** Sets the headers for this request.
    *
    * @note Calling this will wipe out any cookies.
    */
  def headers(newHeaders: Iterable[(String, String)]) = copy(headers = newHeaders)

  /** Sets the headers for this request.  Equivalent to `headers(Seq(...))`. */
  def h(newHeaders: (String, String)*) = headers(newHeaders)

  def addHeader(header: (String, String)) = copy(headers = headers.toVector :+ header)

  def addHeaders(headers: Iterable[(String, String)]) = copy(headers = headers.toVector ++ headers)

  /** Sets the cookies for this request.
    *
    * @note This will wipe out any pre-existing `Cookie` headers.
    * @throws IllegalArgumentException if a cookie-name is not valid.
    */
  def cookies(newCookies: Iterable[(String, String)]) =
    copy(headers = headers.view.filterNot(_._1.equalsIgnoreCase("cookie")).toVector ++ toCookieHeaders(newCookies))

  /** Sets the cookies for this request.  Equivalent to `cookies(Seq(...))`. */
  def c(newCookies: (String, String)*) = cookies(newCookies)

  /** Adds a new `Cookie` header.
    * @note This does not modify existing Cookie headers.
    * @throws IllegalArgumentException if a cookie-name is not valid.
    */
  def addCookie(cookie: (String, String)) =
    addHeader(toCookieHeaders(List(cookie)).head)

  /** Adds new `Cookie` headers.
    * @note This does not modify existing Cookie headers.
    * @throws IllegalArgumentException if a cookie-name is not valid.
    */
  def addCookies(cookies: Iterable[(String, String)]) =
    addHeaders(toCookieHeaders(cookies))

  private def toCookieHeaders(cookies: Iterable[(String, String)]): Iterable[(String, String)] = {
    val sb = new java.lang.StringBuilder
    val result = Vector.newBuilder[(String, String)]
    def flush() {
      sb.setLength(sb.length - 1) // remove the trailing semicolon
      result += "Cookie" -> sb.toString
      sb.setLength(0)
    }
    cookies.foreach { case (name, value) =>
      if(!HttpUtils.isToken(name)) throw new IllegalArgumentException("Not a valid cookie-name: " + name)
      sb.append(name).append('=')
      HttpUtils.quoteInto(sb, value)
      sb.append(";")
      if(sb.length > 128) flush()
    }
    if(sb.length != 0) flush()
    result.result()
  }

  def method(newMethod: String) = copy(method = Some(newMethod))

  /** Sets the "secure" flag on this builder, which controls whether this is an HTTP
    * or HTTPS request.
    *
    * @param newSecure New value for the secure flag
    * @param updatePort Whether to update the port to the default for the new secure setting (default true)
    */
  def secure(newSecure: Boolean, updatePort: Boolean = true) =
    if(updatePort) copy(secure = newSecure, port = RequestBuilder.defaultPort(secure = newSecure))
    else copy(secure = newSecure)

  /** Sets the connection timeout.  Note that this is independent of any liveness ping check. */
  def connectTimeoutMS(newConnectTimeoutMS: Option[Int]): RequestBuilder = copy(connectTimeoutMS = newConnectTimeoutMS)
  def connectTimeoutMS(newConnectTimeoutMS: Int): RequestBuilder = connectTimeoutMS(Some(newConnectTimeoutMS))

  /** Sets the receive timeout -- if the HTTP client blocks for this many milliseconds without receiving
    * anything, an exception is thrown.  Note that this is independent of any liveness ping check. */
  def receiveTimeoutMS(newReceiveTimeoutMS: Option[Int]): RequestBuilder = copy(receiveTimeoutMS = newReceiveTimeoutMS)
  def receiveTimeoutMS(newReceiveTimeoutMS: Int): RequestBuilder = receiveTimeoutMS(Some(newReceiveTimeoutMS))

  /** Sets the whole-lifecycle timeout -- if the HTTP request lasts this many milliseconds, it will be
    * aborted.  Note that this is independent of any liveness ping check. */
  def timeoutMS(newTimeoutMS: Option[Int]): RequestBuilder = copy(timeoutMS = newTimeoutMS)
  def timeoutMS(newTimeoutMS: Int): RequestBuilder = timeoutMS(Some(newTimeoutMS))

  def livenessCheckInfo(newLivenessCheckInfo: Option[LivenessCheckInfo]) = copy(livenessCheckInfo = newLivenessCheckInfo)

  private def finish(methodIfNone: String) = method match {
    case Some(_) => this
    case None => copy(method = Some(methodIfNone))
  }

  def get = new BodylessHttpRequest(this.finish("GET"))

  def delete = new BodylessHttpRequest(this.finish("DELETE"))

  def form(contents: Iterable[(String, String)]) =
    new FormHttpRequest(this.finish("POST"), contents)

  /**
   * @note This does ''not'' take ownership of the input stream.  It must remain open for the
   *       duration of the HTTP request.
   */
  def file(contents: InputStream, file: String = "file", field: String = "file", contentType: String = "application/octet-stream") =
    new FileHttpRequest(this.finish("POST"), contents, file, field, contentType)

  /**
   * @note The iterator must remain valid for the duration of the HTTP request.
   */
  def json(contents: Iterator[JsonEvent]) =
    new JsonHttpRequest(this.finish("POST"), contents)

  /**
   * @note This does ''not'' take ownership of the input stream.  It must remain open for the
   *       duration of the HTTP request.
   */
  def blob(contents: InputStream, contentType: String = "application/octet-stream") =
    new BlobHttpRequest(this.finish("POST"), contents, contentType)

  def url = RequestBuilder.url(this)
}

object RequestBuilder {
  def apply(host: String, secure: Boolean = false) =
    new RequestBuilder(host, secure, defaultPort(secure), Nil, Vector.empty, Vector.empty, None, None, None, None, None)

  private def defaultPort(secure: Boolean) =
    if(secure) 443 else 80

  private[this] val hexDigit = "0123456789ABCDEF".toCharArray
  private[this] val encPB = locally {
    val x = new Array[Boolean](256)
    for(c <- 'a' to 'z') x(c.toInt) = true
    for(c <- 'A' to 'Z') x(c.toInt) = true
    for(c <- '0' to '9') x(c.toInt) = true
    for(c <- ":@-._~!$&'()*+,;=") x(c.toInt) = true
    x
  }
  private[this] val encQB = locally {
    val x = new Array[Boolean](256)
    for(c <- 'a' to 'z') x(c.toInt) = true
    for(c <- 'A' to 'Z') x(c.toInt) = true
    for(c <- '0' to '9') x(c.toInt) = true
    for(c <- "-_.!~*'()") x(c.toInt) = true
    x
  }

  private def enc(sb: java.lang.StringBuilder, s: String, byteAllowed:Array[Boolean]) {
    val bs = s.getBytes("UTF-8")
    var i = 0
    while(i != bs.length) {
      val b = bs(i & 0xff)
      if(byteAllowed(b)) {
        sb.append(b.toChar)
      } else {
        sb.append('%').append(hexDigit(b >>> 4)).append(hexDigit(b & 0xf))
      }
      i += 1
    }
  }

  private def encP(sb: java.lang.StringBuilder, s: String) =
    enc(sb, s, encPB)

  private def encQ(sb: java.lang.StringBuilder, s: String) =
    enc(sb, s, encQB)

  private def url(req: RequestBuilder): String = {
    val sb = new java.lang.StringBuilder

    import req._

    def appendPath() {
      val it = path.iterator
      if(!it.hasNext) sb.append('/')
      else for(pathElement <- it) {
        sb.append('/')
        encP(sb, pathElement)
      }
    }

    def appendQuery() {
      def appendParameter(kv: (String, String)) = {
        encQ(sb, kv._1)
        sb.append('=')
        encQ(sb, kv._2)
      }

      if(query.nonEmpty) {
        sb.append('?')
        val it = query.iterator
        appendParameter(it.next())
        while(it.hasNext) {
          sb.append('&')
          appendParameter(it.next())
        }
      }
    }

    sb.append(if(secure) "https" else "http")
    sb.append("://")
    sb.append(host)
    sb.append(":")
    sb.append(port)
    appendPath()
    appendQuery()

    sb.toString
  }
}

sealed abstract class SimpleHttpRequest(bodyType: String) {
  val builder: RequestBuilder
  override def toString =
    builder.method.getOrElse("[no method]") + " " + builder.url + " with " + bodyType + " body"
}
class BodylessHttpRequest(val builder: RequestBuilder) extends SimpleHttpRequest("no")
class FormHttpRequest(val builder: RequestBuilder, val contents: Iterable[(String, String)]) extends SimpleHttpRequest("form")
class FileHttpRequest(val builder: RequestBuilder, val contents: InputStream, val file: String, val field: String, val contentType: String) extends SimpleHttpRequest("file")
class JsonHttpRequest(val builder: RequestBuilder, val contents: Iterator[JsonEvent]) extends SimpleHttpRequest("JSON")
class BlobHttpRequest(val builder: RequestBuilder, val contents: InputStream, val contentType: String) extends SimpleHttpRequest("blob")
