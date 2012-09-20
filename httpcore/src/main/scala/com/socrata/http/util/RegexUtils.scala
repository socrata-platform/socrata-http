package com.socrata.http
package util

import scala.util.matching.Regex

/* from: http://stackoverflow.com/questions/3021813/how-to-check-whether-a-string-fully-matches-a-regex-in-scala */
object RegexUtils {
  class RegexWithExtensions(underlying: Regex) {
    def matches(string: String) = underlying.pattern.matcher(string).matches
  }
  implicit def regexWithExtensions(regex: Regex) = new RegexWithExtensions(regex)
}
