package com.socrata.http.server.routing

trait Matcher {
  def matches(s: String): Boolean
}

object Matcher {
  class StringMatcher(val target: String) extends Matcher {
    def matches(s: String) = s == target
  }
}

trait Extractor[+T] extends Matcher {
  def extract(s: String): Option[T]
  def matches(s: String) = extract(s).isDefined
}

object Extractor {
  @inline def apply[T](implicit ev: Extractor[T]): ev.type = ev

  implicit object StringExtracter extends Extractor[String] {
    def extract(s: String): Option[String] = Some(s)
  }

  implicit object IntExtractor extends Extractor[Int] {
    private val intMax = java.math.BigInteger.valueOf(Int.MaxValue)
    private val intMin = java.math.BigInteger.valueOf(Int.MinValue)
    def extract(s: String): Option[Int] = {
      val bi =
        try { new java.math.BigInteger(s) }
        catch { case e: NumberFormatException => return None }
      if(bi.compareTo(intMin) >= 0 && bi.compareTo(intMax) <= 0) Some(bi.intValue)
      else None
    }
  }

  implicit object LongExtractor extends Extractor[Long] {
    private val longMax = java.math.BigInteger.valueOf(Long.MaxValue)
    private val longMin = java.math.BigInteger.valueOf(Long.MinValue)
    def extract(s: String): Option[Long] = {
      val bi =
        try { new java.math.BigInteger(s) }
        catch { case e: NumberFormatException => return None }
      if(bi.compareTo(longMin) >= 0 && bi.compareTo(longMax) <= 0) Some(bi.longValue)
      else None
    }
  }
}
