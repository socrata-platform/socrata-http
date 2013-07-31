package com.socrata.http.server.routing

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.WordSpec
import com.socrata.http.server

class SimpleRouterSpec extends WordSpec with ShouldMatchers {
  implicit def path2seq(s: String): Seq[String] = s.split("/")

  "The SimpleRouter" should {

    "route a simple path" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute("some_route") -> 1
      )

      TestRouter(Seq("some_route")) should be (Some(1))
      TestRouter(Seq("some_other_route")) should be (None)
    }

    "route two simple paths" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute("some") -> 1,
        new SimpleRoute("route") -> 2
      )

      TestRouter(Seq("some")) should be (Some(1))
      TestRouter(Seq("route")) should be (Some(2))
      TestRouter(Seq("other")) should be (None)
    }

    "route a nested path" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute("some", "route") -> 1
      )

      TestRouter("some/route") should be (Some(1))
      TestRouter(Seq("some","other","route")) should be (None)
    }

    "route a regex path" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute("(some|route)".r) -> 1
      )

      TestRouter(List("some")) should be (Some(1))
      TestRouter(List("route")) should be (Some(1))
      TestRouter(List("other")) should be (None)
    }

    "take earlier-defined routes as precedent" in {
      object TestRouter extends SimpleRouter (
        new SimpleRoute("some_route") -> 1,
        new SimpleRoute("some_route", "something_else") -> 2
      )

      TestRouter(List("some_route")) should be (Some(1))
      TestRouter(List("some_route", "something_else")) should be (Some(1))
    }
  }

}
