package com.socrata.http.server.ext

import com.socrata.http.server.util.{EntityTag, EntityTagRenderer}

trait IntoResponseParts[R] {
  def intoResponseParts(r: R, parts: ResponseParts): ResponseParts
}

object IntoResponseParts {
  def apply[R](r: R, parts: ResponseParts)(implicit ev: IntoResponseParts[R]) =
    ev.intoResponseParts(r, parts)

  implicit object entityTag extends IntoResponseParts[EntityTag] {
    private val etagHeader = HeaderName("etag")

    def intoResponseParts(etag: EntityTag, parts: ResponseParts): ResponseParts = {
      parts.copy(headers = parts.headers.appendHeader(etagHeader, HeaderValue(EntityTagRenderer(etag))))
    }
  }
}
