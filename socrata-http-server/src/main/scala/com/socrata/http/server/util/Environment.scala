package com.socrata.http.server.util

object RequestFlags extends Enumeration {
  type RequestFlags = Value
  val IsLocal = Value("IsLocal")
  val IsSecure = Value("IsSecure")
}

case class Environment[T](flags: Set[RequestFlags.Value] = Set.empty, appData: T)
