package com.socrata.http.server
package server_impl

import javax.servlet.http.HttpServletResponse

class ChainedHttpResponse(val ops: Seq[HttpResponse]) extends HttpResponse {
  def apply(resp: HttpServletResponse) = ops.foreach(_(resp))

  def ~>(nextResponse: HttpResponse): HttpResponse = nextResponse match {
    case c: ChainedHttpResponse => new ChainedHttpResponse(ops ++ c.ops)
    case _ => new ChainedHttpResponse(ops :+ nextResponse)
  }
}
