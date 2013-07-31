package com.socrata.http.server.util.filters

import com.socrata.http.server.Filter

class EnvironmentProvidingFilter[Env, Req, Resp](envMaker: () => Env) extends Filter[Req, Resp, (Env, Req), Resp] {
  def apply(request: Req, service: ((Env, Req)) => Resp) =
    service((envMaker(), request))
}

object EnvironmentProvidingFilter {
  def apply[Env, Req, Resp](envMaker: => Env) = new EnvironmentProvidingFilter[Env, Req, Resp](() => envMaker)
}
