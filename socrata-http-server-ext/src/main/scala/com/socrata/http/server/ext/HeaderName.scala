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

  def intern(s: String) = {
    require(validHeader(s))
    new HeaderName(s.toLowerCase.intern())
  }

  val Authorization = HeaderName.intern("authorization")
  val Age = HeaderName.intern("age")
  val CacheControl = HeaderName.intern("cache-control")
  val Expires = HeaderName.intern("expires")
  val Pragma = HeaderName.intern("pragma")
  val LastModified = HeaderName.intern("last-modified")
  val ETag = HeaderName.intern("etag")
  val IfMatch = HeaderName.intern("if-match")
  val IfNoneMatch = HeaderName.intern("if-none-match")
  val IfModifiedSince = HeaderName.intern("if-modified-since")
  val IfUnmodifiedSince = HeaderName.intern("if-unmodified-since")
  val Vary = HeaderName.intern("vary")
  val Accept = HeaderName.intern("accept")
  val AcceptEncoding = HeaderName.intern("accept-encoding")
  val AcceptLanguage = HeaderName.intern("accept-language")
  val Expect = HeaderName.intern("expect")
  val Cookie = HeaderName.intern("cookie")
  val SetCookie = HeaderName.intern("set-cookie")
  val ContentDisposition = HeaderName.intern("content-disposition")
  val ContentLength = HeaderName.intern("content-length")
  val ContentType = HeaderName.intern("content-type")
  val ContentEncoding = HeaderName.intern("content-encoding")
  val ContentLanguage = HeaderName.intern("content-language")
  val ContentLocation = HeaderName.intern("content-location")
  val Location = HeaderName.intern("location")
  val Host = HeaderName.intern("host")
  val Referer = HeaderName.intern("referer")
  val Allow = HeaderName.intern("allow")
}
