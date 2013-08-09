package com.socrata.http.server.routing

import scala.language.experimental.macros
import com.socrata.http.server.`routing-impl`.{RouteImpl, DirectoryImpl}
import com.socrata.http.server.HttpService

object Route {
  def apply(pathSpec: String, targetObject: Any): PathTree[String, HttpService] = macro RouteImpl.impl
}

object Directory {
  def apply(pathSpec: String): PathTree[String, HttpService] = macro DirectoryImpl.impl
}
