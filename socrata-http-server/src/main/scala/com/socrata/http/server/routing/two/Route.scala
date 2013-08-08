package com.socrata.http.server.routing.two

import scala.language.experimental.macros
import com.socrata.http.server.`routing-impl`.two.RouteImpl

object Route {
  def apply[U](pathSpec: String)(targetObject: Any) = macro RouteImpl.impl[U]
}

trait Extracter[T] {
  def extract(s: String): Option[T]
}

object Extracter {
  implicit object StringExtracter extends Extracter[String] {
    def extract(s: String): Option[String] = Some(s)
  }
}
