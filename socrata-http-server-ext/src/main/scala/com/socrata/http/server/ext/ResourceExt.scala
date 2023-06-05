package com.socrata.http.server.ext

import com.socrata.http.server.{HttpRequest, HttpResponse}

trait ResourceExt {
  def handle[H : Handler](req: HttpRequest)(h: H): HttpResponse = {
    Handler(h, req) match {
      case Accepted(r) => r
      case Rejected(r) => r
    }
  }
}
