package com.socrata.http.server.util.filters

import com.socrata.http.server._

/** A filter transformer which preserves an environment across a filter which doesn't care about it */
class GreenFilterTransformer[Env, InDown, OutUp, OutDown, InUp](underlying: Filter[InDown, OutUp, OutDown, InUp]) extends Filter[(Env, InDown), OutUp, (Env, OutDown), InUp] {
  def apply(envRequest: (Env, InDown), service: Service[(Env, OutDown), InUp]): OutUp = {
    val (env, req) = envRequest
    val wrappedService = new Service[OutDown, InUp] {
      def apply(req: OutDown) = service((env, req))
    }
    underlying(req, wrappedService)
  }
}
