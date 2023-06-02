package com.socrata.http.server.ext

import java.io.InputStream

import com.socrata.http.server.HttpRequest

trait FromRequest[T] {
  def extract(req: HttpRequest): HandlerDecision[T]
}

object FromRequest {
  def apply[T](req: HttpRequest)(implicit ev: FromRequest[T]): HandlerDecision[T] =
    ev.extract(req)

  implicit def fromRequestParts[T: FromRequestParts]: FromRequest[T] =
    new FromRequest[T] {
      def extract(req: HttpRequest): HandlerDecision[T] =
        FromRequestParts[T](RequestParts.from(req))
    }

  implicit object inputStream extends FromRequest[InputStream] {
    def extract(req: HttpRequest) =
      Accepted(req.inputStream)
  }
}
