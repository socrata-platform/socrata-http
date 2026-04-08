package com.socrata.http.server
package otel

import scala.collection.JavaConverters._

import jakarta.servlet.http.HttpServletResponse
import io.opentelemetry.api.trace.{Span, SpanKind, Tracer}
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder

class OtelHandler private (
  underlying: HttpService,
  tracer: Tracer,
  propagators: ContextPropagators,
  preAttributes: (Span, HttpRequest) => Unit,
  postAttributes: (Span, HttpServletResponse) => Unit
) extends HttpService {
  def apply(req: HttpRequest) = { resp =>
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
  }

  // Standard attributes which can be derived from the request, from
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-server-span
  // This _only_ sets "required" attributes.  If you want to set any more,
  // provide your own function
  private def requiredPreAttributes(span: Span, req: HttpRequest): Unit = {
    span.setAttribute("http.request.method", req.method)
    span.setAttribute("url.scheme", req.servletRequest.getScheme)
    span.setAttribute("url.path", req.requestPathStr)
    req.queryStr.foreach(span.setAttribute("url.query", _))
  }

  // Standard attributes which can be derived from the response, from
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-server-span
  // This _only_ sets "required" attributes.  If you want to set any more,
  // provide your own function
  private def requiredPostAttributes(span: Span, resp: HttpServletResponse): Unit = {
    val status = resp.getStatus
    span.setAttribute("http.response.status_code", status)
    if(status >= 400) {
      // required if the result is an error; per the spec this can
      // just be an HTTP status code.
      span.setAttribute("error.type", status)
    }
  }
}

object OtelHandler {
  def apply(
    tracer: Tracer,
    propagators: ContextPropagators,
    preAttributes: (Span, HttpRequest) => Unit = noop,
    postAttributes: (Span, HttpServletResponse) => Unit = noop
  )(handler: HttpService) =
    new OtelHandler(handler, tracer, propagators, preAttributes, postAttributes)

  private def noop(span: Span, thing: Any): Unit = {}
}
