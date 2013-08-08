package com.socrata.http.server.routing.two

import scala.language.experimental.macros
import com.socrata.http.server.`routing-impl`.two.RouteImpl

class Route {
  type Context

  def path[U](pathSpec: String)(targetObject: Any) = macro RouteImpl.impl[U]
}

trait Extracter[T] {
  def extract(s: String): Option[T]
}

object Extracter {
  implicit object StringExtracter extends Extracter[String] {
    def extract(s: String): Option[String] = Some(s)
  }
}

object Extract {
  def apply[T](p: PathTree[String, List[Any]])(implicit extracter: Extracter[T]): String => Option[PathTree[String, List[Any]]] = { s =>
    extracter.extract(s).map { r => p.map(r :: _) }
  }
}
