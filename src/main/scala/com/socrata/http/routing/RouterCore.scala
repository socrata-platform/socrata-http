package com.socrata.http
package routing

import scala.util.matching.Regex

class PathComponent(val r: Regex)
object PathComponent {
  implicit def str2pcomp(s: String) = new PathComponent(java.util.regex.Pattern.quote(s).r)
  implicit def re2pcomp(r: Regex) = new PathComponent(r)
}

// this exists in case some day we want to swap in or out routers conditionally
// (eg for api versioning)
class RouterSet[+FoundRoute](routers: Seq[Router[FoundRoute]]) extends Router[FoundRoute] {
  def apply(method: String, requestParts: Seq[String]): Option[FoundRoute] = {
    routers.iterator.map(_(method, requestParts)).find(_.isDefined).map(_.get)
  }
}

object RouterSet {
  def apply[FoundRoute](routers: Router[FoundRoute]*) = new RouterSet(routers)
}

class SubRouter[+FoundRoute](prefix: Seq[String], subRouter: Router[FoundRoute]) extends Router[FoundRoute] {
  private val pfxLen = prefix.length
  def apply(method: String, requestParts: Seq[String]): Option[FoundRoute] = {
    if(requestParts.startsWith(prefix)) subRouter(method, requestParts.drop(pfxLen))
    else None
  }
}

object SubRouter {
  def apply[FoundRoute](prefix: String*)(subrouter: Router[FoundRoute]) = new SubRouter(prefix, subrouter)
}
