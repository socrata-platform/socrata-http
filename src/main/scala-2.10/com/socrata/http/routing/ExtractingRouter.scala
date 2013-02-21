package com.socrata.http.routing

import scala.language.experimental.macros

import com.socrata.http.`routing-impl`.ExtractingRouterImpl

object ExtractingRouter {
  // ExtractingRouter[Something]("GET", "/id/?") { id: String => return Something(id) }
  // ExtractingRouter[Something]("GET", "/id/?/*") { (id: String, rest: List[String]) => return Something(id) }
  def apply[U](method: String, route: String)(targetObject: Any): Router[U] = macro ExtractingRouterImpl.impl[U]
}
