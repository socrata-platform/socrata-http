package com.socrata.http.server.routing.two

import scala.language.experimental.macros
import com.socrata.http.server.`routing-impl`.two.RouteImpl
import com.socrata.http.server.HttpService

object Route {
  def apply(pathSpec: String, targetObject: Any): PathTree[String, HttpService] = macro RouteImpl.impl
}

object Routes {
  type R = PathTree[String, HttpService]
  def apply(route: R, routes: R*) = routes.foldLeft(route)(_ merge _)
}
