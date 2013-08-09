package com.socrata.http.server.routing

import com.socrata.http.server.HttpService

object Routes {
  type R = PathTree[String, HttpService]
  def apply(route: R, routes: R*) = routes.foldLeft(route)(_ merge _)
}
