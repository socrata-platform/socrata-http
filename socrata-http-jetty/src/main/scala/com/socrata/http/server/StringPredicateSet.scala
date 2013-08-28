package com.socrata.http.server

// I am a dirty liar.
// This is for dealing with Java code that takes a Set<String> when it really wants
// a predicate on strings.
private class StringPredicateSet(f: String => Boolean) extends java.util.AbstractSet[String] {
  override def contains(s: AnyRef) = { s.isInstanceOf[String] && f(s.asInstanceOf[String]) }
  def size(): Int = ???
  def iterator(): java.util.Iterator[String] = ???
}
