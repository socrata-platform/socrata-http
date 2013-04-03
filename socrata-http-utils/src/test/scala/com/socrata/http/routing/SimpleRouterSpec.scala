package com.socrata.http
package routing

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.WordSpec

class SimpleRouterSpec extends WordSpec with ShouldMatchers {
  implicit def path2seq(s: String): Seq[String] = s.split("/")

  "The SimpleRouter" should {

    "route a simple path" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(HttpMethods.GET, "some_route") -> 1
      )

      TestRouter(HttpMethods.GET, "some_route") should be (Some(1))
      TestRouter(HttpMethods.GET, "some_other_route") should be (None)
    }

    "route two simple paths" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(HttpMethods.GET, "some") -> 1,
        new SimpleRoute(HttpMethods.GET, "route") -> 2
      )
      
      TestRouter(HttpMethods.GET, "some") should be (Some(1))
      TestRouter(HttpMethods.GET, "route") should be (Some(2))
      TestRouter(HttpMethods.GET, "other") should be (None)
    }

    "route a nested path" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(HttpMethods.GET, "some", "route") -> 1
      )

      TestRouter(HttpMethods.GET, "some/route") should be (Some(1))
      TestRouter(HttpMethods.GET, "some/other/route") should be (None)
    }

    "route based on method" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(HttpMethods.GET, "some_route") -> 1,
        new SimpleRoute(HttpMethods.POST, "some_route") -> 2
      )

      TestRouter(HttpMethods.GET, "some_route") should be (Some(1))
      TestRouter(HttpMethods.POST, "some_route") should be (Some(2))
      TestRouter(HttpMethods.DELETE, "some_route") should be (None)
    }

    "route based on a set of methods" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(Set(HttpMethods.PUT, HttpMethods.POST), "some_route") -> 1
      )

      TestRouter(HttpMethods.PUT, "some_route") should be (Some(1))
      TestRouter(HttpMethods.POST, "some_route") should be (Some(1))
      TestRouter(HttpMethods.DELETE, "some_route") should be (None)
    }

    "route a regex path" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(HttpMethods.GET, "(some|route)".r) -> 1
      )

      TestRouter(HttpMethods.GET, "some") should be (Some(1))
      TestRouter(HttpMethods.GET, "route") should be (Some(1))
      TestRouter(HttpMethods.GET, "other") should be (None)
    }

    "take earlier-defined routes as precedent" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute(HttpMethods.GET, "some_route") -> 1,
        new SimpleRoute(Set(HttpMethods.GET, HttpMethods.POST), "some_route") -> 2
      )

      TestRouter(HttpMethods.GET, "some_route") should be (Some(1))
      TestRouter(HttpMethods.POST, "some_route") should be (Some(2))
    }
  }

}
