package com.socrata.http.server.ext

class HeaderName private (val underlying: String) {
  override def toString = underlying
  override def hashCode = underlying.hashCode
  override def equals(that: Any) =
    that match {
      case hn: HeaderName => this.underlying == hn.underlying
      case _ => false
    }
}

object HeaderName {
  private val VALID = (0 to 128).map { i =>
    i.toChar match {
      case '!' | '#' | '$' | '%' | '&' | '\'' | '*' | '+' | '-' | '.' | '^' | '_' | '`' | '|' | '~' => true
      case c if c >= '0' && c <= '9' => true
      case c if c >= 'a' && c <= 'z' => true
      case c if c >= 'A' && c <= 'Z' => true
      case _ => false
    }
  }.toArray

  private def validHeader(s: String): Boolean = {
    var i = 0;
    while(i != s.length) {
      val c = s.charAt(i)
      if(c >= VALID.length || !VALID(c.toInt)) {
        return false
      }
      i += 1
    }
    true
  }

  def apply(s: String) = {
    require(validHeader(s))
    new HeaderName(s.toLowerCase)
  }
}
