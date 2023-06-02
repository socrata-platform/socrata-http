package com.socrata.http.server.ext

class HeaderValue private (val underlying: String) {
  override def toString = underlying
  override def hashCode = underlying.hashCode
  override def equals(that: Any) =
    that match {
      case hn: HeaderName => this.underlying == hn.underlying
      case _ => false
    }
}

object HeaderValue {
  private def validHeaderValue(s: String): Boolean = {
    true // TODO
  }

  def apply(s: String) = {
    require(validHeaderValue(s))
    new HeaderValue(s)
  }
}
