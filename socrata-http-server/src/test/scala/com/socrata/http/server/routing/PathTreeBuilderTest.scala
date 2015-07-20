package com.socrata.http.server.routing

import org.scalatest.{MustMatchers, FunSuite}
import scala.collection.LinearSeq

class PathTreeBuilderTest extends FunSuite with MustMatchers {
  // It would be nice to just make sure that the builders produce the correct pathtrees.
  // Unfortunately, they introduce an anonymous function to do the typecasting from the
  // List[Any] that the pathtree produces to the types that the functions expect, so
  // we can't just use == to compare automatically and manually built trees.  Thus,
  // check the behavior instead.

  test("No patterns -- deprecated flexmatch marker") {
    val pt = PathTreeBuilder[String]("/a/b") { "fix" } merge PathTreeBuilder[String]("/a/b/+") { xs:LinearSeq[String] => ("flex" +: xs).mkString(",") }
    pt(List("a", "b")) must equal (Some("fix"))
    pt(List("a", "b", "c")) must equal (Some("flex,c"))
    pt(List("q", "w")) must equal (None)
  }

  test("No patterns") {
    val pt = PathTreeBuilder[String]("/a/b") { "fix" } merge PathTreeBuilder[String]("/a/b/+") { xs:LinearSeq[String] => ("flex" +: xs).mkString(",") }
    pt(List("a", "b")) must equal (Some("fix"))
    pt(List("a", "b", "c")) must equal (Some("flex,c"))
    pt(List("q", "w")) must equal (None)
  }

  test("Patterns") {
    val pt = PathTreeBuilder[String]("/a/b") { "fix" } merge PathTreeBuilder[String]("/a/{Int}") { i:Int => "pat " + i }
    pt(List("a", "b")) must equal (Some("fix"))
    pt(List("a", "42")) must equal (Some("pat 42"))
    pt(List("a", "c")) must equal (None)
  }

  test("? alias for String") {
    val pt = PathTreeBuilder[String]("/a/?") (identity[String] _)
    pt(List("a", "b")) must equal (Some("b"))
    pt(List("a", "42")) must equal (Some("42"))
    pt(List("a", "")) must equal (Some(""))
    pt(List("a")) must equal (None)
  }

  test("empty") {
    val pt = PathTreeBuilder[String]("/") { "x" }
    pt(List("")) must equal (Some("x"))
  }

  test("initial flexmatch -- deprecated marker") {
    val pt = PathTreeBuilder[Seq[String]]("/+")(identity[Seq[String]] _)
    pt(List("q", "w")) must equal (Some(List("q","w")))
    pt(List("")) must equal (Some(List("")))
    pt(Nil) must equal (None)
  }

  test("initial flexmatch") {
    val pt = PathTreeBuilder[Seq[String]]("/+")(identity[Seq[String]] _)
    pt(List("q", "w")) must equal (Some(List("q","w")))
    pt(List("")) must equal (Some(List("")))
    pt(Nil) must equal (None)
  }

  test("empty component at the end") {
    val pt = PathTreeBuilder[String]("/a/") { "a" }
    pt(List("a")) must equal (None)
    pt(List("a","")) must equal (Some("a"))
  }

  test("empty component in the middle") {
    val pt = PathTreeBuilder[String]("/a//b") { "ab" }
    pt(List("a","b")) must equal (None)
    pt(List("a","","b")) must equal (Some("ab"))
  }

  test("optionally typed component -- default matcher") {
    val pt = PathTreeBuilder[OptionallyTypedPathComponent[String]]("/{{String}}")(identity[OptionallyTypedPathComponent[String]] _)
    pt(List("a")) must equal (Some(OptionallyTypedPathComponent("a", None)))
    pt(List("a.txt")) must equal (Some(OptionallyTypedPathComponent("a", Some("txt"))))
    pt(List("a.")) must equal (Some(OptionallyTypedPathComponent("a", Some(""))))
  }

  test("explicitly optionally typed component -- default matcher") {
    val pt = PathTreeBuilder[OptionallyTypedPathComponent[String]]("/{{String:}}")(identity[OptionallyTypedPathComponent[String]] _)
    pt(List("a")) must equal (Some(OptionallyTypedPathComponent("a", None)))
    pt(List("a.txt")) must equal (Some(OptionallyTypedPathComponent("a", Some("txt"))))
    pt(List("a.")) must equal (Some(OptionallyTypedPathComponent("a", Some(""))))
  }

  test("optionally typed component -- named function") {
    def r(s: String) = "[a-z]+".r.pattern.matcher(s).matches
    val pt = PathTreeBuilder[OptionallyTypedPathComponent[String]]("/{{String:r}}")(identity[OptionallyTypedPathComponent[String]] _)
    pt(List("a")) must equal (Some(OptionallyTypedPathComponent("a", None)))
    pt(List("a.txt")) must equal (Some(OptionallyTypedPathComponent("a", Some("txt"))))
    pt(List("a.")) must equal (None)
    pt(List("a.7")) must equal (None)
  }

  test("typed component -- default matcher") {
    val pt = PathTreeBuilder[TypedPathComponent[String]]("/{{String!}}")(identity[TypedPathComponent[String]] _)
    pt(List("a")) must equal (None)
    pt(List("a.txt")) must equal (Some(TypedPathComponent("a", "txt")))
    pt(List("a.")) must equal (Some(TypedPathComponent("a", "")))
  }

  test("typed component -- named function") {
    def r(s: String) = "[a-z]+".r.pattern.matcher(s).matches
    val pt = PathTreeBuilder[TypedPathComponent[String]]("/{{String!r}}")(identity[TypedPathComponent[String]] _)
    pt(List("a")) must equal (None)
    pt(List("a.txt")) must equal (Some(TypedPathComponent("a", "txt")))
    pt(List("a.")) must equal (None)
    pt(List("a.7")) must equal (None)
  }
}
