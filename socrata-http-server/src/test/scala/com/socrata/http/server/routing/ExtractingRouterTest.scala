package com.socrata.http.server.routing

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers

class ExtractingRouterTest extends FunSuite with MustMatchers {
  test("Without ? or *, the route must match exactly") {
    val r = ExtractingRouter[Int]("/smiling/gnus") { () => 0 }
    r(List("smiling", "gnus")) must equal (Some(0))
    r(List("smiling", "gnus", "jump")) must equal (None)
    r(List("smiling")) must equal (None)
  }

  test("? must be matched by something") {
    val r = ExtractingRouter[String]("/id/?") { (x: String) => x }
    r(List("id", "gnus")) must equal (Some("gnus"))
    r(List("id", "gnus", "jump")) must equal (None)
    r(List("id")) must equal (None)
  }

  test("* may be matched by nothing") {
    val r = ExtractingRouter[List[String]]("/id/*") { (x: List[String]) => x }
    r(List("id")) must equal (Some(Nil))
  }

  test("* may match exactly one thing") {
    val r = ExtractingRouter[List[String]]("/id/*") { (x: List[String]) => x }
    r(List("id","5")) must equal (Some(List("5")))
  }

  test("* may match more than one thing") {
    val r = ExtractingRouter[List[String]]("/id/*") { (x: List[String]) => x }
    r(List("id","5","6","7")) must equal (Some(List("5","6","7")))
  }
}
