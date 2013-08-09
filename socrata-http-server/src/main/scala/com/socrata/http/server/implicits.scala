package com.socrata.http.server

import com.socrata.http.server.`-impl`.ChainedHttpResponse
import javax.servlet.http.HttpServletRequest
import java.net.URLDecoder

object implicits {
  implicit def httpResponseToChainedResponse(resp: HttpResponse) = resp match {
    case c: ChainedHttpResponse => c
    case _ => new ChainedHttpResponse(Vector(resp))
  }

  implicit class PimpedOutRequest(val underlying: HttpServletRequest) extends AnyVal {
    def hostname =
      Option(underlying.getHeader("X-Socrata-Host")).getOrElse(
        Option(underlying.getHeader("Host")).getOrElse("")).split(':').head

    def requestPath: List[String] = // TODO: strip off any context and/or servlet path
      underlying.getRequestURI.split("/", -1 /* I hate you, Java */).iterator.drop(1).map(URLDecoder.decode(_, "UTF-8")).toList
  }
}
