package com.socrata.http.client

import java.io.{InputStream, Closeable}
import java.nio.charset.StandardCharsets

import com.rojoma.simplearm.Managed
import com.rojoma.simplearm.util._
import org.apache.http.entity.ContentType

trait HttpClient extends Closeable {
  trait RawResponse {
    val responseInfo: ResponseInfo
    val body: InputStream
  }

  /**
   * Executes the request.
   *
   * @note Usually, you'll want to use `execute` instead.
   * @return an `InputStream` and the HTTP header info.
   */
  def executeRaw(req: SimpleHttpRequest): Managed[RawResponse] =
    managed(executeRawUnmanaged(req))

  def executeRawUnmanaged(req: SimpleHttpRequest): RawResponse with Closeable

  def executeUnmanaged(req: SimpleHttpRequest): Response with Closeable = {
    val rawResponse = executeRawUnmanaged(req)
    try {
      new StandardResponse(rawResponse.responseInfo, rawResponse.body) with Closeable {
        def close() = rawResponse.close()
      }
    } catch {
      case t: Throwable =>
        try {
          rawResponse.close()
        } catch {
          case t2: Throwable =>
            t.addSuppressed(t2)
        }
        throw t
    }
  }

  /**
   * Executes the request, returning an object which can be used to query the
   * response headers and decode the body.
   */
  def execute(req: SimpleHttpRequest): Managed[Response] =
    managed(executeUnmanaged(req))
}

object HttpClient {
  val jsonContentTypeBase = "application/json"
  val jsonContentType = ContentType.create(jsonContentTypeBase, StandardCharsets.UTF_8)
  val formContentTypeBase = "application/x-www-form-urlencoded"
  val formContentType = ContentType.create(formContentTypeBase, StandardCharsets.UTF_8)
}
