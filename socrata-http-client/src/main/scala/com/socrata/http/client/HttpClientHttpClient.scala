package com.socrata.http.client

import java.lang.reflect.UndeclaredThrowableException
import java.io._
import java.net._
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import javax.net.ssl.SSLException

import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.{CoreProtocolPNames, HttpProtocolParams, HttpConnectionParams}
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.client.methods._
import org.apache.http.entity._
import com.rojoma.simplearm._
import com.rojoma.simplearm.util._

import com.socrata.http.client.exceptions._
import com.socrata.http.common.util.TimeoutManager
import com.socrata.http.`-impl`.NoopCloseable
import com.socrata.http.client.`-impl`._

/** Implementation of [[com.socrata.http.client.HttpClient]] based on Apache HttpComponents. */
class HttpClientHttpClient(livenessChecker: LivenessChecker,
                           executor: Executor,
                           continueTimeout: Option[Int] = None,
                           userAgent: String = "HttpClientHttpClient")
  extends HttpClient
{
  import HttpClient._

  private[this] val httpclient = locally {
    val connManager = new PoolingClientConnectionManager
    connManager.setDefaultMaxPerRoute(Int.MaxValue)
    connManager.setMaxTotal(Int.MaxValue)
    new DefaultHttpClient(connManager)
  }
  @volatile private[this] var initialized = false
  private val log = org.slf4j.LoggerFactory.getLogger(classOf[HttpClientHttpClient])
  private val timeoutManager = new TimeoutManager(executor)

  private def init() {
    def reallyInit() = synchronized {
      if(!initialized) {
        val params = httpclient.getParams
        HttpProtocolParams.setUserAgent(params, userAgent)
        continueTimeout match {
          case Some(timeout) =>
            HttpProtocolParams.setUseExpectContinue(params, true)
            params.setIntParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE, timeout) // no option for this one?
          case None =>
            HttpProtocolParams.setUseExpectContinue(params, false)
        }
        timeoutManager.start()
        initialized = true
      }
    }
    if(!initialized) reallyInit()
  }

  def close() {
    try {
      httpclient.getConnectionManager.shutdown()
    } finally {
      timeoutManager.close()
    }
  }

  // Pending the addition of this functionality in simple-arm
  private class ResourceScope extends Closeable {
    private var things: List[(Any, Resource[Any])] = Nil

    def open[T](f: => T)(implicit ev: Resource[T]): T = {
      val thing = f
      try {
        things = (thing, ev.asInstanceOf[Resource[Any]]) :: things
      } catch {
        case t: Throwable =>
          try { ev.closeAbnormally(thing, t) }
          catch { case t2: Throwable => t.addSuppressed(t2) }
          throw t
      }
      thing
    }
    def close() {
      try {
        while(things.nonEmpty) {
          val toClose = things.head
          things = things.tail
          toClose._2.close(toClose._1)
        }
      } catch {
        case t: Throwable =>
          while(things.nonEmpty) {
            val toClose = things.head
            things = things.tail
            try {
              toClose._2.close(toClose._1)
            } catch {
              case t2: Throwable => t.addSuppressed(t2)
            }
          }
          throw t
      }
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

    val scope = new ResourceScope
    try {
      scope.open {
        pingTarget match {
          case Some(target) => livenessChecker.check(target) { abortReason = LivenessCheck; req.abort() }
          case None => NoopCloseable
        }
      }
      scope.open {
        timeout match {
          case Some(ms) => timeoutManager.addJob(ms) { abortReason = FullTimeout; req.abort() }
          case None => NoopCloseable
        }
      }

      val response = try {
        httpclient.execute(req)
      } catch {
        case _: ConnectTimeoutException =>
          connectTimeout()
        case e: ConnectException =>
          connectFailed(e)
        case e: UndeclaredThrowableException =>
          throw e.getCause
        case e: SocketException if e.getMessage == "Socket closed" =>
          probablyAborted(e)
        case e: InterruptedIOException if e.getMessage == "Connection already shutdown" =>
          probablyAborted(e)
        case e: IOException if e.getMessage == "Request already aborted" =>
          probablyAborted(e)
        case e: SSLException =>
          probablyAborted(e)
        case _: SocketTimeoutException =>
          receiveTimeout()
      }

      try {
        if(log.isTraceEnabled) {
          log.trace("<<< {}", response.getStatusLine)
          log.trace("<<< {}", Option(response.getFirstHeader("Content-type")).getOrElse("[no content type]"))
          log.trace("<<< {}", Option(response.getFirstHeader("Content-length")).getOrElse("[no content length]"))
        }

        val entity = response.getEntity
        val content = if(entity != null) scope.open(entity.getContent()) else EmptyInputStream
        new RawResponse with Closeable {
          val eofWatcher = new EOFWatchingInputStream(content)
          val body = CatchingInputStream(new BufferedInputStream(eofWatcher)) {
            case e: SocketException if e.getMessage == "Socket closed" =>
              probablyAborted(e)
            case e: InterruptedIOException if e.getMessage == "Connection already shutdown" =>
              probablyAborted(e)
            case e: SSLException =>
              probablyAborted(e)
            case e: java.net.SocketTimeoutException =>
              receiveTimeout()
          }
          val responseInfo = new ResponseInfo {
            val resultCode = response.getStatusLine.getStatusCode
            // I am *fairly* sure (from code-diving) that the value field of a header
            // parsed from a response will never be null.
            def headers(name: String) = response.getHeaders(name).map(_.getValue)
            lazy val headerNames = response.getAllHeaders.iterator.map(_.getName.toLowerCase).toSet
          }
          def close() {
            if(!eofWatcher.eofReached) req.abort()
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
    val params = op.getParams
    req.builder.connectTimeoutMS.foreach { ms =>
      HttpConnectionParams.setConnectionTimeout(params, ms)
    }
    req.builder.receiveTimeoutMS.foreach { ms =>
      HttpConnectionParams.setSoTimeout(params, ms)
    }
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
    val sendEntity = new MultipartEntity
    sendEntity.addPart(req.field, new InputStreamBody(req.contents, req.contentType, req.file))
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
