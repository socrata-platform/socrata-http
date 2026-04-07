package com.socrata.http.server
package otel

import scala.collection.JavaConverters._

import io.opentelemetry.api.trace.{Span, SpanKind, Tracer}
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder

class OtelHandler private (
  underlying: HttpService,
  tracer: Tracer,
  propagators: ContextPropagators,
  attributes: (Span, HttpRequest) => Unit
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
        attributes(Span.current, req)
        underlying(req)(resp)
      }
  }
}

object OtelHandler {
  def apply(
    tracer: Tracer,
    propagators: ContextPropagators,
    attributes: (Span, HttpRequest) => Unit = standardAttributes
  )(handler: HttpService) =
    new OtelHandler(handler, tracer, propagators, attributes)

  def standardAttributes(span: Span, req: HttpRequest): Unit = {
    span.setAttribute("component", "http")
    span.setAttribute("http.method", req.method)
    span.setAttribute("http.scheme", "http")
    span.setAttribute("http.target", req.requestPathStr)
  }
}
