package com.socrata.http.server.ext

class RequestId private [ext] (val underlying: String) {
  override def toString = underlying
  override def hashCode = underlying.hashCode
  override def equals(o: Any) =
    o match {
      case that: RequestId => this.underlying == that.underlying
      case _ => false
    }
}
