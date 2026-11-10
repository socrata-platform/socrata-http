package com.socrata.http.server.routing

import org.scalatest.{MustMatchers, FunSuite}

import com.socrata.http.server.{HttpRequest, HttpResponse}
import com.socrata.http.server.routing.SimpleRouteContext._

class RouteIntrospectionTest extends FunSuite with MustMatchers {
  object widgets extends SimpleResource {
    override def get = _ => null
  }

  private def routeFns[R](t: PathTree[R]) = t.leaves.collect { case rf: RouteFunction[_] => rf }.toList

  test("Route records its path spec and target") {
    val fns = routeFns(Route("/v1/widgets", widgets))
    fns.map(_.pathSpec) must equal (List("/v1/widgets"))
    fns.head.target.asInstanceOf[AnyRef] must be theSameInstanceAs widgets
  }

  test("Parameterised routes record the path spec and the factory function") {
    val factory = { (i: Int) => widgets : com.socrata.http.server.HttpService }
    val fns = routeFns(Route("/v1/widgets/{Int}", factory))
    fns.map(_.pathSpec) must equal (List("/v1/widgets/{Int}"))
    fns.head.target.asInstanceOf[AnyRef] must be theSameInstanceAs factory
  }

  test("Merged trees expose every leaf") {
    val t = Routes(Route("/a", widgets), Route("/a/{Int}/b", { (i: Int) => widgets : com.socrata.http.server.HttpService }), Route("/c/+", { (xs: List[String]) => widgets : com.socrata.http.server.HttpService }))
    routeFns(t).map(_.pathSpec).toSet must equal (Set("/a", "/a/{Int}/b", "/c/+"))
  }

  test("withAttribute attaches metadata without changing routing") {
    val t = Route("/v1/widgets", widgets).withAttribute("hello")
    routeFns(t).head.attributes must equal (Vector("hello"))
    t(List("v1", "widgets")) must be ('defined)
    t(List("v1", "nope")) must be (None)
  }
}
