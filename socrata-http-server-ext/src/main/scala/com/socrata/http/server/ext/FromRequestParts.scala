package com.socrata.http.server.ext

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.http.server.util.Precondition

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

  implicit object requestId extends FromRequestParts[RequestId] {
    def extract(req: RequestParts): HandlerDecision[RequestId] =
      Accepted(req.requestId)
  }

  implicit object preconditionId extends FromRequestParts[Precondition] {
    def extract(req: RequestParts): HandlerDecision[Precondition] =
      Accepted(req.precondition)
  }
}
