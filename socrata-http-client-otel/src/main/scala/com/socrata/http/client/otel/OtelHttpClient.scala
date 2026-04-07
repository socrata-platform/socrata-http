package com.socrata.http.client
package otel

import java.io.Closeable

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.incubator.propagation.ExtendedContextPropagators
import io.opentelemetry.context.propagation.ContextPropagators

class OtelHttpClient private (underlying: HttpClient, propagators: ContextPropagators) extends HttpClient {
  override def close() = underlying.close()

  override def executeRawUnmanaged(req: SimpleHttpRequest): RawResponse with Closeable = {
    new RawResponse with Closeable {
      val resp = underlying.executeRawUnmanaged(augment(req))

      override def close() = resp.close()
      override val body = resp.body
      override val responseInfo = resp.responseInfo
    }
  }

  private def augment(req: SimpleHttpRequest): SimpleHttpRequest = {
    req match {
      case bhr: BodylessHttpRequest =>
        new BodylessHttpRequest(addHeaders(bhr.builder))
      case fhr: FormHttpRequest =>
        new FormHttpRequest(addHeaders(fhr.builder), fhr.contents)
      case fhr: FileHttpRequest =>
        new FileHttpRequest(addHeaders(fhr.builder), fhr.contents, fhr.file, fhr.field, fhr.contentType)
      case jhr: JsonHttpRequest =>
        new JsonHttpRequest(addHeaders(jhr.builder), jhr.contents)
      case bhr: BlobHttpRequest =>
        new BlobHttpRequest(addHeaders(bhr.builder), bhr.contents, bhr.contentType)
    }
  }

  private def addHeaders(req: RequestBuilder): RequestBuilder = {
    val headers = Vector.newBuilder[(String, String)]
    ExtendedContextPropagators.getTextMapPropagationContext(propagators)
      .forEach { (k, v) => headers += k -> v }
    req.addHeaders(headers.result())
  }
}

object OtelHttpClient {
  def apply(underlying: HttpClient, propagators: ContextPropagators) =
    new OtelHttpClient(underlying, propagators)
}
