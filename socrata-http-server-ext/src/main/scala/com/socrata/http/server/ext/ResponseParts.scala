package com.socrata.http.server.ext

import com.socrata.http.server.HttpResponse

case class ResponseParts(headers: HeaderMap) {
  def ~>(resp: HttpResponse): HttpResponse =
    headers ~> resp
}

object ResponseParts {
  val default = ResponseParts(new HeaderMap)
}
