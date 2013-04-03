package com.socrata.http.server

import com.socrata.http.server.server_impl._

object implicits {
  implicit def httpResponseToChainedResponse(resp: HttpResponse) = resp match {
    case c: ChainedHttpResponse => c
    case _ => new ChainedHttpResponse(Vector(resp))
  }
}
