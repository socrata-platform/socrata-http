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
    var i = 0;
    while(i != s.length) {
      val c = s.charAt(i)
      if(c < 32 || c > 127) return false
    }
    true
  }

  def apply(s: String) = {
    require(validHeaderValue(s))
    new HeaderValue(s)
  }

  def apply(hv: HeaderValue) = {
    new HeaderValue(hv.underlying)
  }
}
