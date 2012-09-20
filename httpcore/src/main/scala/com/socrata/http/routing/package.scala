package com.socrata.http

package object routing {
  type Route = (String, Seq[String]) => Boolean // first parameter is method, rest is path
  type Router[+FoundRoute] = (String, Seq[String]) => Option[FoundRoute]
}
