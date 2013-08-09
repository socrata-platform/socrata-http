package com.socrata.http.server.routing

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite

class DirectoryTest extends FunSuite with MustMatchers {
  // TODO: improve this to ensure that the generated code does the right thing
  // (will need a mock HttpServletRequest)
  test("Directory compiles") {
    Directory("/foo")
    Directory("/foo/{String}")
    Directory("/foo/{String}/*")
  }
}
