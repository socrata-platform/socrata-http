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
import com.socrata.http.client.`-impl`.{CatchingInputStream, ReaderInputStream, JsonEventIteratorReader, FormReader}

/** Implementation of [[com.socrata.http.client.HttpClient]] based on Apache HttpComponents. */
class HttpClientHttpClient(livenessChecker: LivenessChecker,
                           executor: Executor,
                           continueTimeout: Option[Int] = Some(3000),
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

  private def send[A](req: HttpUriRequest, timeout: Option[Int], pingTarget: Option[LivenessCheckTarget], f: RawResponse => A): A = {
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

    for {
      _ <- managed {
        pingTarget match {
          case Some(target) => livenessChecker.check(target) { abortReason = LivenessCheck; req.abort() }
          case None => NoopCloseable
        }
      }
      _ <- managed {
        timeout match {
          case Some(ms) => timeoutManager.addJob(ms) { abortReason = FullTimeout; req.abort() }
          case None => NoopCloseable
        }
      }
    } yield {
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

      if(log.isTraceEnabled) {
        log.trace("<<< {}", response.getStatusLine)
        log.trace("<<< {}", Option(response.getFirstHeader("Content-type")).getOrElse("[no content type]"))
        log.trace("<<< {}", Option(response.getFirstHeader("Content-length")).getOrElse("[no content length]"))
      }

      val entity = response.getEntity
      if(entity != null) {
        val content = entity.getContent()
        try {
          val catchingInputStream = CatchingInputStream(content) {
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
          f((responseInfo, catchingInputStream))
        } catch {
          case e: Exception =>
            req.abort()
            throw e
        } finally {
          content.close()
        }
      } else {
        noBodyInResponse()
      }
    }
  }

  def executeRaw(req: SimpleHttpRequest): Managed[RawResponse] = {
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

  def processBodyless(req: BodylessHttpRequest) = new SimpleArm[RawResponse] {
    def flatMap[A](f: RawResponse => A): A = {
      init()
      val op = bodylessOp(req)
      send(op, req.builder.timeoutMS, pingTarget(req), f)
    }
  }

  def processForm(req: FormHttpRequest): Managed[RawResponse] = new SimpleArm[RawResponse] {
    def flatMap[A](f: RawResponse => A): A = {
      init()
      val sendEntity = new InputStreamEntity(new ReaderInputStream(new FormReader(req.contents), StandardCharsets.UTF_8), -1, formContentType)
      sendEntity.setChunked(true)
      val op = bodyEnclosingOp(req)
      op.setEntity(sendEntity)
      send(op, req.builder.timeoutMS, pingTarget(req), f)
    }
  }

  def processFile(req: FileHttpRequest): Managed[RawResponse] = new SimpleArm[RawResponse] {
    def flatMap[A](f: RawResponse => A): A = {
      init()
      val sendEntity = new MultipartEntity
      sendEntity.addPart(req.field, new InputStreamBody(req.contents, req.contentType, req.file))
      val op = bodyEnclosingOp(req)
      op.setEntity(sendEntity)
      send(op, req.builder.timeoutMS, pingTarget(req), f)
    }
  }

  def processBlob(req: BlobHttpRequest): Managed[RawResponse] = new SimpleArm[RawResponse] {
    def flatMap[A](f: RawResponse => A): A = {
      init()
      val sendEntity = new InputStreamEntity(req.contents, -1, ContentType.create(req.contentType))
      val op = bodyEnclosingOp(req)
      op.setEntity(sendEntity)
      send(op, req.builder.timeoutMS, pingTarget(req), f)
    }
  }

  def processJson(req: JsonHttpRequest): Managed[RawResponse] = new SimpleArm[RawResponse] {
    def flatMap[A](f: RawResponse => A): A = {
      init()
      val sendEntity = new InputStreamEntity(new ReaderInputStream(new JsonEventIteratorReader(req.contents), StandardCharsets.UTF_8), -1, jsonContentType)
      sendEntity.setChunked(true)
      val op = bodyEnclosingOp(req)
      op.setEntity(sendEntity)
      send(op, req.builder.timeoutMS, pingTarget(req), f)
    }
  }
}
