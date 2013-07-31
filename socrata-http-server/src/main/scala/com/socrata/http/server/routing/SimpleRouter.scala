package com.socrata.http.server.routing

import com.socrata.http.`-impl`.implicits._

class SimpleRoute(routeWrapped: PathComponent*) extends Route {
  val route = routeWrapped.map(_.r)

  def apply(requestParts: Seq[String]) =
    requestParts.lengthCompare(route.length) >= 0 && (route, requestParts).zipped.forall { _ matches _ }
}

class SimpleRouter[+FoundRoute](routeSpecs: (Route, FoundRoute)*) extends Router[FoundRoute] {
  def apply(requestParts: Seq[String]) =
    routeSpecs.find(_._1(requestParts)).map(_._2)
}
