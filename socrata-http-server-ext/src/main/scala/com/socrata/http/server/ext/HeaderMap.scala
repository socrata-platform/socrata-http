package com.socrata.http.server.ext

import jakarta.servlet.http.HttpServletResponse

import com.socrata.http.server.HttpResponse

class HeaderMap private(underlying: Map[HeaderName, Vector[HeaderValue]]) {
  def this() = this(Map.empty)

  def setHeader(name: HeaderName, value: HeaderValue): HeaderMap = {
    new HeaderMap(underlying + (name -> Vector(value)))
  }

  def appendHeader(name: HeaderName, value: HeaderValue): HeaderMap = {
    new HeaderMap(underlying + (name -> (underlying.getOrElse(name, Vector.empty) :+ value)))
  }

  def lookup(header: HeaderName): Vector[HeaderValue] =
    underlying.getOrElse(header, Vector.empty)

  override def toString = "Header" + underlying.toString

  def ~>(resp: HttpResponse): HttpResponse = { (rawResp: HttpServletResponse) =>
    for {
      (k, vs) <- underlying
      v <- vs
    } {
      rawResp.addHeader(k.underlying, v.underlying)
    }
    resp(rawResp)
  }
}

object HeaderMap {
  implicit object headerMap extends FromRequestParts[HeaderMap] {
    def extract(req: RequestParts): HandlerDecision[HeaderMap] =
      Accepted(req.headers)
  }
}
