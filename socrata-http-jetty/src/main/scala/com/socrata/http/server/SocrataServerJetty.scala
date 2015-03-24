package com.socrata.http.server

class SocrataServerJetty(handler: HttpService, options: SocrataServerJetty.Options) extends
  AbstractSocrataServerJetty(new FunctionHandler(handler), options)

object SocrataServerJetty {
  type Options = AbstractSocrataServerJetty.Options
  val defaultOptions = AbstractSocrataServerJetty.defaultOptions
  val Gzip = AbstractSocrataServerJetty.Gzip
  val Pool = AbstractSocrataServerJetty.Pool
}
