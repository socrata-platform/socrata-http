package com.socrata.http.server.routing

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import scala.collection.LinearSeq

class PathTreeBuilderTest extends FunSuite with MustMatchers {
  test("No patterns") {
    val pt = PathTreeBuilder[String](1, "/a/b") { "fix" } merge PathTreeBuilder[String](0, "/a/b/*") { xs:LinearSeq[String] => ("flex" +: xs).mkString(",") }
    pt(List("a", "b")) must equal (Some("fix"))
    pt(List("a", "b", "c")) must equal (Some("flex,c"))
    pt(List("q", "w")) must equal (None)
  }

  test("Patterns") {
    val pt = PathTreeBuilder[String](1, "/a/b") { "fix" } merge PathTreeBuilder[String](0, "/a/{Int}") { i:Int => "pat " + i }
    pt(List("a", "b")) must equal (Some("fix"))
    pt(List("a", "42")) must equal (Some("pat 42"))
    pt(List("a", "c")) must equal (None)
  }

  test("initial flexmatch") {
    val pt = PathTreeBuilder[Seq[String]](1, "/*")(identity[Seq[String]] _)
    pt(List("q", "w")) must equal (Some(List("q","w")))
    pt(Nil) must equal (Some(Nil))
  }

  test("empty component at the end") {
    val pt = PathTreeBuilder[String](1, "/a/") { "a" }
    pt(List("a")) must equal (None)
    pt(List("a","")) must equal (Some("a"))
  }

  test("empty component in the middle") {
    val pt = PathTreeBuilder[String](1, "/a//b") { "ab" }
    pt(List("a","b")) must equal (None)
    pt(List("a","","b")) must equal (Some("ab"))
  }
}
