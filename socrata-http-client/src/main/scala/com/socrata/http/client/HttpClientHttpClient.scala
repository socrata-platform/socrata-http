package com.socrata.http.client

import java.lang.reflect.UndeclaredThrowableException
import java.io._
import java.net._
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import javax.net.ssl._

import com.rojoma.simplearm.v2._
import org.apache.commons.io.input.ReaderInputStream
import org.apache.http.HttpHost
import org.apache.http.client.methods._
import org.apache.http.config.{RegistryBuilder, Registry}
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.socket.{PlainConnectionSocketFactory, ConnectionSocketFactory}
import org.apache.http.entity._
import org.apache.http.impl.client.{DefaultConnectionKeepAliveStrategy, HttpClients}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager

import com.socrata.http.client.exceptions._
import com.socrata.http.common.util.TimeoutManager
import com.socrata.http.`-impl`.NoopCloseable
import com.socrata.http.client.`-impl`._
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.client.config.RequestConfig
import org.apache.http.entity.mime.MultipartEntityBuilder
import scala.util.{Success, Try}


/** Implementation of [[com.socrata.http.client.HttpClient]] based on Apache HttpComponents. */
class HttpClientHttpClient(executor: Executor, options: HttpClientHttpClient.Options = HttpClientHttpClient.defaultOptions)
  extends HttpClient
{
  import HttpClient._
  import options._

  private[this] val sslFactory = SSLConnectionSocketFactory.getSystemSocketFactory

  private[this] val socketFactoryRegistry: Registry[ConnectionSocketFactory] =
    RegistryBuilder
      .create()
      .register("https", sslFactory.asInstanceOf[ConnectionSocketFactory])
      .register("http", PlainConnectionSocketFactory.getSocketFactory)
      .build()

  private[this] val connectionManager = locally {
    val connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry)
    connManager.setDefaultMaxPerRoute(Int.MaxValue)
    connManager.setMaxTotal(Int.MaxValue)
    connManager
  }

  private[this] val httpclient = locally {
    val builder =
      HttpClients.custom().
        disableAutomaticRetries().
        disableCookieManagement().
        disableAuthCaching().
        setConnectionManager(connectionManager).
        setUserAgent(userAgent).
        setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE).
        setSSLSocketFactory(sslFactory).
        setProxy(proxy.orNull)
    if(!contentCompression) builder.disableContentCompression()
    if(!followRedirects) builder.disableRedirectHandling()
    builder.build()
  }

  @volatile private[this] var initialized = false
  private val log = org.slf4j.LoggerFactory.getLogger(classOf[HttpClientHttpClient])
  private val timeoutManager = new TimeoutManager(executor)

  private def init() {
    def reallyInit() = synchronized {
      if(!initialized) {
        timeoutManager.start()
        initialized = true
      }
    }
    if(!initialized) reallyInit()
  }

  def close() {
    try {
      connectionManager.shutdown()
    } finally {
      timeoutManager.close()
    }
  }

  private def send[A](req: HttpUriRequest, timeout: Option[Int], pingTarget: Option[LivenessCheckTarget]): RawResponse with Closeable = {
    val LivenessCheck = 0
    val FullTimeout = 1
    @volatile var abortReason: Int = -1 // this can be touched from another thread

    def probablyAborted(e: Exception): Nothing = {
      abortReason match {
        case LivenessCheck => livenessCheckFailed()
        case FullTimeout => fullTimeout()
        case -1 => throw e // wasn't us
        case other => sys.error("Unknown abort reason " + other)
      }
    }

    val scope = new ResourceScope("http client")
    try {
      pingTarget.foreach { target =>
        scope.open { livenessChecker.check(target) { abortReason = LivenessCheck; req.abort() } }
      }
      timeout.foreach { ms =>
        scope.open { timeoutManager.addJob(ms) { abortReason = FullTimeout; req.abort() } }
      }

      val response = try {
        scope.open(httpclient.execute(req))
      } catch {
        case _: ConnectTimeoutException =>
          connectTimeout()
        case e: ConnectException =>
          connectFailed(e)
        case e: UndeclaredThrowableException =>
          throw e.getCause
        case e: SocketException if e.getMessage == "Socket closed" =>
          probablyAborted(e)
        case e: SocketTimeoutException =>
          receiveTimeout(e)
        case e: InterruptedIOException =>
          probablyAborted(e)
        case e: IOException if e.getMessage == "Request already aborted" =>
          probablyAborted(e)
        case e: SSLException =>
          probablyAborted(e)
      }

      try {
        if(log.isTraceEnabled) {
          log.trace("<<< {}", response.getStatusLine)
          log.trace("<<< {}", Option(response.getFirstHeader("Content-type")).getOrElse("[no content type]"))
          log.trace("<<< {}", Option(response.getFirstHeader("Content-length")).getOrElse("[no content length]"))
        }

        val entity = response.getEntity
        val content = if(entity != null) entity.getContent() else EmptyInputStream
        new RawResponse with Closeable {
          var exceptionWhileReading = false
          val body = CatchingInputStream(new BufferedInputStream(content)) {
            case e: SocketException if e.getMessage == "Socket closed" =>
              exceptionWhileReading = true
              probablyAborted(e)
            case e: InterruptedIOException if e.getMessage == "Connection already shutdown" =>
              exceptionWhileReading = true
              probablyAborted(e)
            case e: SSLException =>
              exceptionWhileReading = true
              probablyAborted(e)
            case e: java.net.SocketTimeoutException =>
              exceptionWhileReading = true
              receiveTimeout(e)
            case e: Throwable =>
              exceptionWhileReading = true
              throw e
          }
          val responseInfo = new ResponseInfo {
            val resultCode = response.getStatusLine.getStatusCode
            // I am *fairly* sure (from code-diving) that the value field of a header
            // parsed from a response will never be null.
            def headers(name: String) = response.getHeaders(name).map(_.getValue)
            lazy val headerNames = response.getAllHeaders.iterator.map(_.getName.toLowerCase).toSet
          }
          def close() {
            // So... there is no way to ask "have we consumed the entire response?"
            // even though in almost all cases HTTP responses are framed by either
            // content-length or transfer-encoding.  So we'll try to read a single
            // extra byte to see if we've reached EOF -- if not, we'll assume there's
            // a nontrivial amount to go, and hard-abort the connection.
            //
            // If the connection is aborted, it cannot be kept alive to be re-used,
            // which is why we don't just abort unconditionally.
            if(exceptionWhileReading || Try(body.read()) != Success(-1)) {
              Try(req.abort()) // ignore any exceptions, we're closing anyway
            }
            scope.close()
          }
        }
      } catch {
        case t: Throwable =>
          try { req.abort() }
          catch { case t2: Throwable => t2.addSuppressed(t) }
          throw t
      }
    } catch {
      case t: Throwable =>
        try { scope.close() }
        catch { case t2: Throwable => t.addSuppressed(t2) }
        throw t
    }
  }

  def executeRawUnmanaged(req: SimpleHttpRequest): RawResponse with Closeable = {
    log.trace(">>> {}", req)
    req match {
      case bodyless: BodylessHttpRequest => processBodyless(bodyless)
      case form: FormHttpRequest => processForm(form)
      case file: FileHttpRequest => processFile(file)
      case json: JsonHttpRequest => processJson(json)
      case blob: BlobHttpRequest => processBlob(blob)
    }
  }

  def pingTarget(req: SimpleHttpRequest): Option[LivenessCheckTarget] = req.builder.livenessCheckInfo match {
    case Some(lci) => Some(new LivenessCheckTarget(InetAddress.getByName(req.builder.host), lci.port, lci.response))
    case None => None
  }

  def setupOp(req: SimpleHttpRequest, op: HttpRequestBase) {
    for((k, v) <- req.builder.headers) op.addHeader(k, v)
    val config = RequestConfig.custom()
    config.setExpectContinueEnabled(false)
    req.builder.connectTimeoutMS match {
      case Some(ms) =>
        config.setConnectTimeout(ms max 1) // if <= 0, treat it as 1 so that you get the shortest possible timeout
      case None =>
        config.setConnectTimeout(0)
    }
    req.builder.receiveTimeoutMS match {
      case Some(ms) =>
        config.setSocketTimeout(ms max 1) // if <= 0, treat it as 1 so that you get the shortest possible timeout
      case None =>
        config.setSocketTimeout(0)
    }
    op.setConfig(config.build())
  }

  def bodylessOp(req: SimpleHttpRequest): HttpRequestBase = req.builder.method match {
    case Some(m) =>
      val op = new HttpRequestBase {
        setURI(new URI(req.builder.url))
        def getMethod = m
      }
      setupOp(req, op)
      op
    case None =>
      throw new IllegalArgumentException("No method in request")
  }

  def bodyEnclosingOp(req: SimpleHttpRequest): HttpEntityEnclosingRequestBase = req.builder.method match {
    case Some(m) =>
      val op = new HttpEntityEnclosingRequestBase {
        setURI(new URI(req.builder.url))
        def getMethod = m
      }
      setupOp(req, op)
      op
    case None =>
      throw new IllegalArgumentException("No method in request")
  }

  def processBodyless(req: BodylessHttpRequest): RawResponse with Closeable = {
    init()
    val op = bodylessOp(req)
    send(op, req.builder.timeoutMS, pingTarget(req))
  }

  def processForm(req: FormHttpRequest): RawResponse with Closeable = {
    init()
    val sendEntity = new InputStreamEntity(new ReaderInputStream(new FormReader(req.contents), StandardCharsets.UTF_8), -1, formContentType)
    sendEntity.setChunked(true)
    val op = bodyEnclosingOp(req)
    op.setEntity(sendEntity)
    send(op, req.builder.timeoutMS, pingTarget(req))
  }

  def processFile(req: FileHttpRequest): RawResponse with Closeable = {
    init()
    val sendEntity = MultipartEntityBuilder.create().addBinaryBody(req.field, req.contents, ContentType.parse(req.contentType), req.file).build()
    val op = bodyEnclosingOp(req)
    op.setEntity(sendEntity)
    send(op, req.builder.timeoutMS, pingTarget(req))
  }

  def processBlob(req: BlobHttpRequest): RawResponse with Closeable = {
    init()
    val sendEntity = new InputStreamEntity(req.contents, -1, ContentType.create(req.contentType))
    val op = bodyEnclosingOp(req)
    op.setEntity(sendEntity)
    send(op, req.builder.timeoutMS, pingTarget(req))
  }

  def processJson(req: JsonHttpRequest): RawResponse with Closeable = {
    init()
    val sendEntity = new InputStreamEntity(new ReaderInputStream(new JsonEventIteratorReader(req.contents), StandardCharsets.UTF_8), -1, jsonContentType)
    sendEntity.setChunked(true)
    val op = bodyEnclosingOp(req)
    op.setEntity(sendEntity)
    send(op, req.builder.timeoutMS, pingTarget(req))
  }
}

object HttpClientHttpClient {
  abstract class Options {
    val livenessChecker: LivenessChecker
    def withLivenessChecker(lc: LivenessChecker): Options

    val userAgent: String
    def withUserAgent(ua: String): Options

    val sslContext: SSLContext
    def withSSLContext(sc: SSLContext): Options

    val contentCompression: Boolean
    def withContentCompression(enabled: Boolean): Options

    val proxy: Option[HttpHost]
    def withProxy(prx: Option[HttpHost]): Options
    def withProxy(host: String, port: Int): Options = withProxy(Some(new HttpHost(host, port)))

    val followRedirects: Boolean
    def withFollowRedirects(fr: Boolean): Options
  }

  private case class OptionsImpl(
    livenessChecker: LivenessChecker = NoopLivenessChecker,
    userAgent: String = "HttpClientHttpClient",
    sslContext: SSLContext = SSLContext.getDefault,
    contentCompression: Boolean = false,
    proxy: Option[HttpHost] = None,
    followRedirects: Boolean = true
  ) extends Options {
    def withLivenessChecker(lc: LivenessChecker) = copy(livenessChecker = lc)
    def withUserAgent(ua: String) = copy(userAgent = ua)
    def withSSLContext(sc: SSLContext) = copy(sslContext = sc)
    def withContentCompression(enabled: Boolean) = copy(contentCompression = enabled)
    def withProxy(prx: Option[HttpHost]): Options = copy(proxy = prx)
    def withFollowRedirects(fr: Boolean): Options = copy(followRedirects = fr)
  }

  val defaultOptions: Options = OptionsImpl()
}
