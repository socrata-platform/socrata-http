package com.socrata.http.server.routing

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import scala.collection.LinearSeq
import org.scalatest.prop.PropertyChecks

class PathTree2Test extends FunSuite with MustMatchers with PropertyChecks {
  def pl[R](xs: String*)(f: R, wantMore: Boolean = false): PathTree2[R] = {
    PathTree2(xs, f, wantMore)
  }

  def pm[R](xs: String*)(f: R, wantMore: Boolean = false): PathTree2[R] = {
    // build a matcher of string literals which cannot be recognized as such
    PathTree2(xs.map { x => new Matcher { def matches(s: String) = s == x; override def toString = com.rojoma.json.ast.JString(x).toString } }, f, wantMore)
  }

  def sm(s: String) = new Matcher.StringMatcher(s)
  def es = Extractor[String]
  def ei = Extractor[Int]

  test("A literal pathtree that doesn't want more matches itself") {
    forAll { (xs: List[String]) =>
      whenever(xs.nonEmpty) {
        val r = pl(xs : _*)("literal-non-flex")
        r.accept(xs) must equal (Some((Nil, "literal-non-flex")))
      }
    }
  }

  test("A literal pathtree that wants more will not match itself") {
    forAll { (prefix: List[String]) =>
      val r = pl(prefix : _*)("literal-flex", wantMore = true)
      r.accept(prefix) must equal(None)
    }
  }

  test("A literal pathtree that wants more matches anything which has itself as prefix a non-empty suffix, and receives the suffix") {
    forAll { (prefix: List[String], tail: List[String]) =>
      whenever(tail.nonEmpty) {
        val r = pl(prefix : _*)("literal-flex", wantMore = true)
        r.accept(prefix ::: tail) must equal(Some(List(tail), "literal-flex"))
      }
    }
  }

  test("A literal pathtree that doesn't want more fails to match anything different") {
    forAll { (xs: List[String], ys: List[String]) =>
      whenever(xs != ys) {
        val r = pl(xs : _*)("literal-flex")
        r.accept(ys) must equal(None)
      }
    }
  }

  test("Two identical fixed literal pathtrees will match the one on the right") {
    val r1 = pl("a","b")("r1")
    val r2 = pl("a","b")("r2")

    (r1 merge r2).accept(List("a", "b")) must equal (Some(Nil, "r2"))
    (r2 merge r1).accept(List("a", "b")) must equal (Some(Nil, "r1"))
  }

  test("Two identical fixed flex literal pathtrees will match the one on the right") {
    val r1 = pl("a","b")("r1", wantMore = true)
    val r2 = pl("a","b")("r2", wantMore = true)

    (r1 merge r2).accept(List("a", "b", "c")) must equal (Some(List(List("c")), "r2"))
    (r2 merge r1).accept(List("a", "b", "c")) must equal (Some(List(List("c")), "r1"))
  }

  test("Given two matching literal pathtrees, the longer one wins even when the shorter is on the right") {
    val r1 = pl("a", "b", "c", "d", "e")("r1")
    val r2 = pl("a", "b", "c")("r2", wantMore = true)
    (r1 merge r2).accept(List("a","b","c","d","e")) must equal (Some(Nil, "r1"))
    (r2 merge r1).accept(List("a","b","c","d","e")) must equal (Some(Nil, "r1"))
  }

  // Now repeat all the above but with matching trees!  The only two cases are all-literal and all-matching,
  // because a literal is converted to a matching when matching occurs.

  test("A matching pathtree that doesn't want more matches itself") {
    forAll { (xs: List[String]) =>
      whenever(xs.nonEmpty) {
        val r = pm(xs : _*)("literal-non-flex")
        r.accept(xs) must equal (Some((Nil, "literal-non-flex")))
      }
    }
  }

  test("A matching pathtree that wants more will not match itself") {
    forAll { (prefix: List[String]) =>
      val r = pm(prefix : _*)("literal-flex", wantMore = true)
      r.accept(prefix) must equal(None)
    }
  }

  test("A matching pathtree that wants more matches anything which has itself as prefix a non-empty suffix, and receives the suffix") {
    forAll { (prefix: List[String], tail: List[String]) =>
      whenever(tail.nonEmpty) {
        val r = pm(prefix : _*)("literal-flex", wantMore = true)
        r.accept(prefix ::: tail) must equal(Some(List(tail), "literal-flex"))
      }
    }
  }

  test("A matching pathtree that doesn't want more fails to match anything different") {
    forAll { (xs: List[String], ys: List[String]) =>
      whenever(xs != ys) {
        val r = pm(xs : _*)("literal-flex")
        r.accept(ys) must equal(None)
      }
    }
  }

  test("Two identical fixed matching pathtrees will match the one on the right") {
    val r1 = pm("a","b")("r1")
    val r2 = pm("a","b")("r2")

    (r1 merge r2).accept(List("a", "b")) must equal (Some(Nil, "r2"))
    (r2 merge r1).accept(List("a", "b")) must equal (Some(Nil, "r1"))
  }

  test("Two identical fixed flex matching pathtrees will match the one on the right") {
    val r1 = pm("a","b")("r1", wantMore = true)
    val r2 = pm("a","b")("r2", wantMore = true)

    (r1 merge r2).accept(List("a", "b", "c")) must equal (Some(List(List("c")), "r2"))
    (r2 merge r1).accept(List("a", "b", "c")) must equal (Some(List(List("c")), "r1"))
  }

  test("Given two matching matching pathtrees, the longer one wins even when the shorter is on the right") {
    val r1 = pm("a", "b", "c", "d", "e")("r1")
    val r2 = pm("a", "b", "c")("r2", wantMore = true)
    (r1 merge r2).accept(List("a","b","c","d","e")) must equal (Some(Nil, "r1"))
    (r2 merge r1).accept(List("a","b","c","d","e")) must equal (Some(Nil, "r1"))
  }

  test("An extracting match returns the found objects in the order in which they were extracted") {
    val r1 = PathTree2(List(sm("hello"), es, ei, sm("world")), { xs: List[Any] => "extracted" :: xs }, flex = false)
    r1(List("hello","there","42","world")) must equal (Some(List("extracted", "there", 42)))

    val r2 = PathTree2(List(sm("hello"), es, ei, sm("world")), { xs: List[Any] => "extracted" :: xs }, flex = true)
    r2(List("hello","there","42","world","gnu")) must equal (Some(List("extracted", "there", 42, List("gnu"))))
  }

  test("adjacent identical extractors merge") {
    val r1 = PathTree2(List(sm("hello"), es, sm("world")), "r1", flex = false)
    val r2 = PathTree2(List(sm("hello"), es, sm("there")), "r2", flex = false)
    val r3 = PathTree2(List(sm("hello"), ei), "r3", flex = false)

    (r1 merge r2 merge r3) must equal (
      LiteralOnlyPathTree2(
        Map(
          "hello" -> MatchingPathTree2(
            List(
              es -> LiteralOnlyPathTree2(
                Map(
                  "world" -> LiteralOnlyPathTree2(Map.empty, Some("r1"), None),
                  "there" -> LiteralOnlyPathTree2(Map.empty, Some("r2"), None)),
                None, None),
              ei -> LiteralOnlyPathTree2(Map.empty, Some("r3"), None)),
            None, None)),
        None, None)
    )
  }
}
