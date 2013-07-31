package com.socrata.http.client

import java.io.{InputStream, Closeable}
import java.nio.charset.StandardCharsets

import com.rojoma.simplearm.{SimpleArm, Managed}
import org.apache.http.entity.ContentType

trait HttpClient extends Closeable {
  type RawResponse = (ResponseInfo, InputStream)

  /**
   * Executes the request.
   *
   * @note Usually, you'll want to use `execute` instead.
   * @return an `InputStream` and the HTTP header info.
   */
  def executeRaw(req: SimpleHttpRequest): Managed[RawResponse]

  /**
   * Executes the request, returning an object which can be used to query the
   * response headers and decode the body.
   */
  def execute(req: SimpleHttpRequest): Managed[Response] =
    new SimpleArm[Response] {
      def flatMap[A](f: Response => A): A =
        for(rawResponse <- executeRaw(req)) yield {
          val cooked = new StandardResponse(rawResponse._1, rawResponse._2)
          f(cooked)
        }
    }
}

object HttpClient {
  val jsonContentTypeBase = "application/json"
  val jsonContentType = ContentType.create(jsonContentTypeBase, StandardCharsets.UTF_8)
  val formContentTypeBase = "application/x-www-form-urlencoded"
  val formContentType = ContentType.create(formContentTypeBase)
}
