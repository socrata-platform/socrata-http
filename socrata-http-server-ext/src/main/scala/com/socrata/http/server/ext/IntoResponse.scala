package com.socrata.http.server.ext

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

trait IntoResponse[R] {
  def intoResponse(r: R): HttpResponse
}

object IntoResponse extends `-impl`.IntoResponseImpl {
  def apply[R](r: R)(implicit ev: IntoResponse[R]) =
    ev.intoResponse(r)

  implicit object httpResponse extends IntoResponse[HttpResponse] {
    def intoResponse(r: HttpResponse): HttpResponse = r
  }
}
