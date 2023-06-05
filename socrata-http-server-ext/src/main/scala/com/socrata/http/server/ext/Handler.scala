package com.socrata.http.server.ext

import com.socrata.http.server.{HttpRequest, HttpResponse}

trait Handler[H] {
  def invoke(h: H, r: HttpRequest): HandlerDecision[HttpResponse]
}

object Handler extends `-impl`.HandlerImpl {
  def apply[H](h: H, r: HttpRequest)(implicit ev: Handler[H]) = ev.invoke(h, r)
}
