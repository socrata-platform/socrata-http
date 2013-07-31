package com.socrata.http.`-impl`

import scala.util.matching.Regex

private[http] object implicits {
  implicit class RegexWithExtensions(val underlying: Regex) extends AnyVal {
    def matches(string: String) = underlying.pattern.matcher(string).matches
  }
}
