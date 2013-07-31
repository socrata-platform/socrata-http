package com.socrata.http.server

import com.socrata.http.server.`-impl`.ChainedHttpResponse
import javax.servlet.http.HttpServletRequest

object implicits {
  implicit def httpResponseToChainedResponse(resp: HttpResponse) = resp match {
    case c: ChainedHttpResponse => c
    case _ => new ChainedHttpResponse(Vector(resp))
  }

  implicit class PimpedOutRequest(val underlying: HttpServletRequest) extends AnyVal {
    def hostname =
      Option(underlying.getHeader("X-Socrata-Host")).getOrElse(
        Option(underlying.getHeader("Host")).getOrElse("")).split(':').head
  }
}
