package com.socrata.http.server.routing

import scala.language.experimental.macros

import com.socrata.http.server.`routing-impl`.ExtractingRouterImpl

object ExtractingRouter {
  // ExtractingRouter[Something]("/id/?") { id: String => return Something(id) }
  // ExtractingRouter[Something]("/id/?/*") { (id: String, rest: List[String]) => return Something(id) }
  def apply[U](route: String)(targetObject: Any): Router[U] = macro ExtractingRouterImpl.impl[U]
}
