package com.socrata.http.server
package otel

import scala.collection.JavaConverters._

import jakarta.servlet.http.HttpServletResponse
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.{Span, SpanKind, Tracer}
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder

class OtelHandler private (
  underlying: HttpService,
  tracer: Tracer,
  propagators: ContextPropagators,
  preAttributes: (Span, HttpRequest) => Unit,
  postAttributes: (Span, HttpServletResponse) => Unit,
  record: (HttpRequest) => Boolean
) extends HttpService {
  private val keys = OtelHandler.Keys

  def apply(req: HttpRequest) = { resp =>
    if(record(req)) {
      tracer.spanBuilder(s"${req.method} ${req.requestPathStr}")
        .asInstanceOf[ExtendedSpanBuilder]
        .setParentFrom(
          propagators,
          req.servletRequest.getHeaderNames.asScala.map { h =>
            h -> req.header(h).get
          }.toMap.asJava
        )
        .setSpanKind(SpanKind.SERVER)
        .startAndRun { () =>
          val span = Span.current

          requiredPreAttributes(span, req)
          preAttributes(span, req)

          underlying(req)(resp)

          requiredPostAttributes(span, resp)
          postAttributes(span, resp)
        }
    } else {
      underlying(req)(resp)
    }
  }

  // Standard attributes which can be derived from the request, from
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-server-span
  // This _only_ sets "required" attributes.  If you want to set any more,
  // provide your own function
  private def requiredPreAttributes(span: Span, req: HttpRequest): Unit = {
    span.setAttribute(keys.httpRequestMethod, req.method)
    span.setAttribute(keys.urlFull, req.servletRequest.getRequestURL.toString)
    span.setAttribute(keys.urlScheme, req.servletRequest.getScheme)
    span.setAttribute(keys.urlPath, req.requestPathStr)
    req.queryStr.foreach(span.setAttribute(keys.urlQuery, _))
  }

  // Standard attributes which can be derived from the response, from
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-server-span
  // This _only_ sets "required" attributes.  If you want to set any more,
  // provide your own function
  private def requiredPostAttributes(span: Span, resp: HttpServletResponse): Unit = {
    val status = resp.getStatus
    span.setAttribute(keys.httpResponseStatusCode, status)
    if(status >= 400) {
      // required if the result is an error; per the spec this can
      // just be an HTTP status code.
      span.setAttribute(keys.errorType, status)
    }
  }
}

object OtelHandler {
  private object Keys {
    val httpRequestMethod = AttributeKey.stringKey("http.request.method")
    val urlFull = AttributeKey.stringKey("url.full")
    val urlScheme = AttributeKey.stringKey("url.scheme")
    val urlPath = AttributeKey.stringKey("url.path")
    val urlQuery = AttributeKey.stringKey("url.query")

    val httpResponseStatusCode = AttributeKey.longKey("http.response.status_code")
    val errorType = AttributeKey.longKey("error.type")
  }

  def apply(
    tracer: Tracer,
    propagators: ContextPropagators,
    preAttributes: (Span, HttpRequest) => Unit = noop,
    postAttributes: (Span, HttpServletResponse) => Unit = noop,
    record: (HttpRequest) => Boolean = yes
  )(handler: HttpService) =
    new OtelHandler(handler, tracer, propagators, preAttributes, postAttributes, record)

  private def noop(span: Span, thing: Any): Unit = {}

  private def yes(req: HttpRequest) = true
}
