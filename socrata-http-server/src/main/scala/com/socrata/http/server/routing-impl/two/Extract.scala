package com.socrata.http.server.`routing-impl`.two

import com.socrata.http.server.routing.two.{Extracter, PathTree}

object Extract {
  def apply[T](p: PathTree[String, List[Any]])(implicit extracter: Extracter[T]): String => Option[PathTree[String, List[Any]]] = { s =>
    extracter.extract(s).map { r => p.map(r :: _) }
  }
}
