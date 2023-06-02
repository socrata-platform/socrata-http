package com.socrata.http.server.ext

trait IntoResponseParts[R] {
  def intoResponseParts(r: R, parts: ResponseParts): ResponseParts
}

object IntoResponseParts {
  def apply[R](r: R, parts: ResponseParts)(implicit ev: IntoResponseParts[R]) =
    ev.intoResponseParts(r, parts)
}
