package com.socrata.http.server

trait ServerBroker {
  type Cookie
  def register(port: Int): Cookie
  def deregister(cookie: Cookie)
}

object ServerBroker {
  object Noop extends ServerBroker {
    type Cookie = Unit
    def register(port: Int) {}
    def deregister(cookie: Unit) {}
  }
}
