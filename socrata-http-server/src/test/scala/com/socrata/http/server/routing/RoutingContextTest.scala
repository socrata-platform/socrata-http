package com.socrata.http.server.routing

import org.scalatest.{FunSuite,MustMatchers}

import com.socrata.http.server.{Service, HttpService, HttpResponse}
import javax.servlet.http.HttpServletRequest

class RoutingContextTest extends FunSuite with MustMatchers {
  // TODO: improve this to ensure that the generated code does the right thing
  // (will need a mock HttpServletRequest)
  test("Directory compiles") {
    import SimpleRouteContext._
    Directory("/foo")
    Directory("/foo/{String}")
    Directory("/foo/{String}/{String}")
  }

  test("Route compiles") {
    import SimpleRouteContext._
    val noop: HttpService = null
    Route("/foo", noop)(List("foo")) must equal (Some(null))
    Route("/foo", noop)(List("bar")) must equal (None)
    Route("/foo", noop)(List("foo","bar")) must equal (None)
    Route("/a/{String}", (s: String) => noop)(List("a","b")) must equal (Some(null))
    Route("/a/{String}/+", (s: String, xs: Seq[String]) => noop)(List("a","b","c")) must equal (Some(null))
  }

  test("Wrapped service compiles") {
    type Serv = Service[(Int, HttpServletRequest), HttpResponse]
    val ctx = new RouteContext[(Int, HttpServletRequest), HttpResponse]
    import ctx._
    val noop: Serv = null
    Directory("/foo")
    Directory("/foo/{String}")
    Route("/foo", noop)(List("foo")) must equal (Some(null))
    Route("/foo", noop)(List("bar")) must equal (None)
    Route("/foo", noop)(List("foo","bar")) must equal (None)
    Route("/a/{String}", (s: String) => noop)(List("a","b")) must equal (Some(null))
    Route("/a/{String}/+", (s: String, xs: Seq[String]) => noop)(List("a","b","c")) must equal (Some(null))
  }

  test("A bunch of routes compile") {
    import SimpleRouteContext._
    val one: HttpService = req => resp => ()
    val two: HttpService = req => resp => ()
    val rs = Routes(
      Route("/foo", one),
      Route("/bar", two)
    )
    rs(List("foo")) must equal (Some(one))
    rs(List("bar")) must equal (Some(two))
  }
}
