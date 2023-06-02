package com.socrata.http.server.ext

private[ext] class LazyBox[T](x: => T) {
  lazy val get: T = x
}
