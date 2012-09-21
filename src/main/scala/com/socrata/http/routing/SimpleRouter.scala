package com.socrata.http
package routing

import util.RegexUtils._

class SimpleRoute(acceptsMethods: String => Boolean, routeWrapped: PathComponent*) extends Route {
  def this(method: String, route: PathComponent*) = this(Set(method), route: _*)

  val route = routeWrapped.map(_.r)

  def acceptsRoute(requestParts: Seq[String]) =
    requestParts.lengthCompare(route.length) >= 0 && (route, requestParts).zipped.forall { _ matches _ }

  def apply(method: String, requestParts: Seq[String]) =
    acceptsMethods(method) && acceptsRoute(requestParts)
}

class SimpleRouter[+FoundRoute](routeSpecs: (Route, FoundRoute)*) extends Router[FoundRoute] {
  def apply(method: String, requestParts: Seq[String]) =
    routeSpecs.find(_._1(method, requestParts)).map(_._2)
}
