package com.socrata.http.server.`-impl`

import javax.servlet.http.HttpServletResponse

import com.socrata.http.server.HttpResponse

// result of an implicit; therefore public
class ChainedHttpResponse(val ops: Seq[HttpResponse]) extends HttpResponse {
  def apply(resp: HttpServletResponse): Unit = ops.foreach(_(resp))

  def ~>(nextResponse: HttpResponse): HttpResponse = nextResponse match { // scalastyle:ignore method.name
    case c: ChainedHttpResponse => new ChainedHttpResponse(ops ++ c.ops)
    case _ => new ChainedHttpResponse(ops :+ nextResponse)
  }
}
