package com.socrata.http.server.`-impl`

import jakarta.servlet.http.HttpServletResponse

import com.socrata.http.server.HttpResponse

// result of an implicit; therefore public
class ChainedHttpResponse(val ops: Seq[HttpResponse]) extends HttpResponse {
  def apply(resp: HttpServletResponse) = ops.foreach(_(resp))

  def ~>(nextResponse: HttpResponse): HttpResponse = nextResponse match {
    case c: ChainedHttpResponse => new ChainedHttpResponse(ops ++ c.ops)
    case _ => new ChainedHttpResponse(ops :+ nextResponse)
  }
}
