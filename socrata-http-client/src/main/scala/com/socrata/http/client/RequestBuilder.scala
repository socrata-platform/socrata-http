package com.socrata.http.client

import java.io.{ByteArrayOutputStream, InputStream}

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.JsonEncode
import com.rojoma.json.v3.io.{JsonEvent, JValueEventIterator}
import com.socrata.http.common.util.HttpUtils
import com.socrata.http.common.livenesscheck.LivenessCheckInfo
import java.net.{URL, URI, URISyntaxException}
import java.nio.charset.{CharacterCodingException, StandardCharsets}
import java.nio.ByteBuffer
import org.apache.commons.codec.binary.Base64

/**
 * Conventions:
 *  - Updaters which simply name the component ''replace'' any existing values.
 *  - Updaters which are the first letter of the component are convenience variadic aliases that also ''replace'' any existing values.
 *  - Updaters which are called `addX` ''augment'' any existing values with a single item.
 *  - Updaters which are caleld `addXs` ''augment'' any existing values with a collection of items.
 */
final class RequestBuilder private (val host: String,
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

  def path(newPath: Iterable[String]) = copy(path = newPath)

  def addPath(newPath: String) = copy(path = path.toVector :+ newPath)

  def addPaths(newPath: Iterable[String]) = copy(path = path.toVector ++ newPath)

  def p(newPath: String*) = copy(path = newPath)

  /** Sets the query parameters for this request. */
  def query(newQuery: Iterable[(String, String)]) = copy(query = newQuery)

  /** Sets the query parameters for this request.  Equivalent to `query(Seq(...))`. */
  def q(newQuery: (String, String)*) = query(newQuery)

  /** Adds a query parameter to this request. */
  def addParameter(parameter: (String, String)) = copy(query = query.toVector :+ parameter)

  /** Adds query parameters to this request. */
  def addParameters(parameters: Iterable[(String, String)]) = copy(query = query.toVector ++ parameters)

  /** Sets the headers for this request.
    *
    * @note Calling this will wipe out any cookies.
    */
  def headers(newHeaders: Iterable[(String, String)]) = copy(headers = newHeaders)

  /** Sets the headers for this request.  Equivalent to `headers(Seq(...))`. */
  def h(newHeaders: (String, String)*) = headers(newHeaders)

  def addHeader(header: (String, String)) = copy(headers = headers.toVector :+ header)

  def addHeaders(newHeaders: Iterable[(String, String)]) = copy(headers = headers.toVector ++ newHeaders)

  def addBasicAuth(username:String, password:String) = {
    val userAndPassBytes= s"""$username:$password""".getBytes
    val base64Bytes = Base64.encodeBase64String(userAndPassBytes)
    addHeader(("Authorization", s"""Basic $base64Bytes"""))
  }
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

  @deprecated(message = "Use \"jsonBody\" instead", since = "3.1.1")
  def jsonValue[T : JsonEncode](contents: JValue) = jsonBody(contents)

  def jsonBody[T : JsonEncode](contents: T) =
    new JsonHttpRequest(this.finish("POST"), JValueEventIterator(JsonEncode.toJValue(contents)))

  /**
   * @note This does ''not'' take ownership of the input stream.  It must remain open for the
   *       duration of the HTTP request.
   */
  def blob(contents: InputStream, contentType: String = "application/octet-stream") =
    new BlobHttpRequest(this.finish("POST"), contents, contentType)

  def url = RequestBuilder.url(this)
}

object RequestBuilder {
  def apply(host: String, secure: Boolean = false): RequestBuilder =
    new RequestBuilder(host, secure, defaultPort(secure), Nil, Vector.empty, Vector.empty, None, None, None, None, None)

  /** Converts a Java `URI` object to a RequestBuilder.
    * @throws IllegalArgumentException if the URL is not an absolute HTTP or HTTPS URI, or if a query parameter does
    *                                  not have an `=` character, or if any percent-encoding is not UTF-8.
    */
  def apply(uri: URI): RequestBuilder = {
    val host = uri.getHost
    if(host eq null) throw new IllegalArgumentException("The URI has no host")
    val secure = uri.getScheme match {
      case "http" => false
      case "https" => true
      case _ => throw new IllegalArgumentException("The URI must be an HTTP or HTTPS URI")
    }
    val port = uri.getPort match {
      case -1 => defaultPort(secure)
      case n => n
    }
    val path = Option(uri.getRawPath).getOrElse("/").split("/",-1).drop(1).map(decP)
    val query = Option(uri.getRawQuery).map(_.split("&", -1)).getOrElse(new Array[String](0)).map { kv =>
      kv.split("=", 2) match {
        case Array(a) => throw new IllegalArgumentException("All query parameters must have both a key and a value")
        case Array(a, b) => (decQ(a), decQ(b))
      }
    }
    new RequestBuilder(
      host,
      secure,
      port,
      path,
      query,
      Vector.empty,
      None,
      None,
      None,
      None,
      None
    )
  }

  /** Converts a Java `URL` object to a RequestBuilder.
    * @throws IllegalArgumentException if the URL cannot be converted to a URI, or for any reason `apply(java.net.URI)` can throw.
    */
  def apply(url: URL): RequestBuilder =
    try {
      apply(url.toURI)
    } catch {
      case e: URISyntaxException =>
        throw new IllegalArgumentException("URL cannot be converted to URI", e)
    }

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
      val b = bs(i) & 0xff
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

  private def decodeHex(c: Char): Int = {
    if(c >= '0' && c <= '9') c - '0'
    else if(c >= 'A' && c <= 'F') 10 + c - 'A'
    else if(c >= 'a' && c <= 'f') 10 + c - 'a'
    else throw new IllegalArgumentException("Expected hex digit, got " + c)
  }

  private def decodePct(s: String, i: Int): Int = {
    try {
      val h1 = s.charAt(i + 1)
      val h2 = s.charAt(i + 2)
      (decodeHex(h1) << 4) + decodeHex(h2)
    } catch {
      case e: IndexOutOfBoundsException =>
        throw new IllegalArgumentException("End of input while looking for two hex digits")
    }
  }

  private class NCBaos extends ByteArrayOutputStream {
    def toByteBuffer = ByteBuffer.wrap(buf, 0, count)
  }

  private def decP(s: String): String = {
    val baos = new NCBaos
    var i = 0
    while(i != s.length) {
      val c = s.charAt(i)
      if(c == '%') {
        baos.write(decodePct(s, i))
        i += 3
      } else {
        baos.write(c.toInt)
        i += 1
      }
    }
    stringify(baos.toByteBuffer)
  }

  private def stringify(xs: ByteBuffer) = try {
    StandardCharsets.UTF_8.newDecoder.decode(xs).toString
  } catch {
    case e: CharacterCodingException =>
      throw new IllegalArgumentException("Percent-encoding is not UTF-8", e)
  }

  private def decQ(s: String): String = {
    val baos = new NCBaos
    var i = 0
    while(i != s.length) {
      val c = s.charAt(i)
      if(c == '%') {
        baos.write(decodePct(s, i))
        i += 3
      } else if(c == '+') {
        baos.write(' ')
        i += 1
      } else {
        baos.write(c.toInt)
        i += 1
      }
    }
    stringify(baos.toByteBuffer)
  }

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

    if(host.indexOf(':') == -1 || (host.startsWith("[") && host.endsWith("]"))) sb.append(host)
    else sb.append('[').append(host).append(']')

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
