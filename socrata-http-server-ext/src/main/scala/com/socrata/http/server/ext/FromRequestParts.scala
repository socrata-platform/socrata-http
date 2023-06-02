package com.socrata.http.server.ext

import com.rojoma.simplearm.v2.ResourceScope

trait FromRequestParts[T] {
  def extract(req: RequestParts): HandlerDecision[T]
}

object FromRequestParts {
  def apply[T](req: RequestParts)(implicit ev: FromRequestParts[T]): HandlerDecision[T] =
    ev.extract(req)

  implicit object resourceScope extends FromRequestParts[ResourceScope] {
    def extract(req: RequestParts): HandlerDecision[ResourceScope] =
      Accepted(req.resourceScope)
  }
}
