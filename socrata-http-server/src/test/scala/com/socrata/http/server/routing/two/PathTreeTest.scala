package com.socrata.http.server.routing.two

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import scala.collection.LinearSeq

class PathTreeTest extends FunSuite with MustMatchers {
  def p[R](priority: Int, wantMore: Boolean, xs: String*)(f: Seq[String] => R): PathTree[String, R] = {
    def mapTree(tree: PathTree[String, LinearSeq[String]])(realSeg: String): Option[PathTree[String, LinearSeq[String]]] =
      Some(tree.map(realSeg +: _))

    xs.foldRight(if(wantMore) PathTree.flexRoot(priority, identity[LinearSeq[String]]) else PathTree.fixRoot[String](priority, LinearSeq.empty[String])) { (seg, tree) =>
      if(seg eq null) {
        new PathTree(Map.empty, List(mapTree(tree)), Map.empty)
      } else {
        new PathTree(Map(seg -> tree), Nil, Map.empty)
      }
    }.map(f)
  }

  test("An empty pathtree that doesn't want more matches an empty path") {
    val r = p(0, wantMore = false) { xs => xs }
    r(Nil) must equal(Some(Nil))
  }

  test("An empty pathtree that wants more matches an empty path") {
    val r = p(0, wantMore = true) { xs => xs }
    r(Nil) must equal(Some(Nil))
  }

  test("An empty pathtree that wants more matches an arbitrary path") {
    val r = p(0, wantMore = true) { xs => xs }
    r(List("a", "b", "c")) must equal(Some(List("a", "b", "c")))
  }

  test("Two identical-but-for-priority pathtrees will match the higher priority") {
    val r1 = p(0, false, "a", "b") { xs => "r1" }
    val r2 = p(1, false, "a", "b") { xs => "r2" }

    (r1 merge r2)(List("a", "b")) must equal (Some("r2"))
    (r2 merge r1)(List("a", "b")) must equal (Some("r2"))
  }

  test("Given two matching pathtrees, the longer one wins even when the shorter has a higher priority") {
    val r1 = p(0, false, "a", "b", "c", "d", "e") { xs => "r1" }
    val r2 = p(1, true, "a", "b", "c") { xs => "r2" }
    (r1 merge r2)(List("a","b","c","d","e")) must equal (Some("r1"))
  }

  test("Given two pathnames, one of which has a hole, the one without the hole matches when it is higher priority") {
    val r1 = p(0, true, "a", null, "c", "d") { xs => ("r1", xs) }
    val r2 = p(1, true, "a", "b", "c", "d") { xs => ("r2", xs) }
    (r1 merge r2)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(("r2", List("e","f","g"))))
    (r2 merge r1)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(("r2", List("e","f","g"))))
  }

  test("Given two pathnames, one of which has a hole, the one with the hole matches when it is higher priority") {
    val r1 = p(1, true, "a", null, "c", "d") { xs => ("r1", xs) }
    val r2 = p(0, true, "a", "b", "c", "d") { xs => ("r2", xs) }
    (r1 merge r2)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(("r1", List("b", "e","f","g"))))
    (r2 merge r1)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(("r1", List("b", "e","f","g"))))
  }

  test("Given two pathnames, one of which accepts extra data and the other doesn't, an extra-long path returns the one which accepts when it is higher pri") {
    val r1 = p(0, false, "a", "b", "c", "d") { xs => Left("r1") }
    val r2 = p(1, true, "a", "b", "c", "d") { xs => Right(xs) }
    (r1 merge r2)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(Right(List("e", "f", "g"))))
    (r2 merge r1)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(Right(List("e", "f", "g"))))
  }

  test("Given two pathnames, one of which accepts extra data and the other doesn't, an extra-long path returns the one which accepts when it is lower pri") {
    val r1 = p(1, false, "a", "b", "c", "d") { xs => Left("r1") }
    val r2 = p(0, true, "a", "b", "c", "d") { xs => Right(xs) }
    (r1 merge r2)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(Right(List("e", "f", "g"))))
    (r2 merge r1)(List("a", "b", "c", "d", "e", "f", "g")) must equal (Some(Right(List("e", "f", "g"))))
  }
}
