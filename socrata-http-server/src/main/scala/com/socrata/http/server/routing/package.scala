package com.socrata.http.server

package object routing {
  type Route = Seq[String] => Boolean // parameter is path
  type Router[+FoundRoute] = Seq[String] => Option[FoundRoute]
}
