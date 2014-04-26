package com.socrata.http.server.routing

import org.scalatest.{MustMatchers, FunSuite}
import org.scalatest.prop.PropertyChecks

class ExtractorTest extends FunSuite with MustMatchers with PropertyChecks {
  test("Extractor[Int] doesn't match numbers out of Int's range") {
    forAll { i: Long =>
      whenever(i != 0) {
        if(i > 0) Extractor[Int].extract((BigInt(Int.MaxValue) + i).toString) must equal (None)
        else Extractor[Int].extract((BigInt(Int.MinValue) + i).toString) must equal (None)
      }
    }
  }

  test("Extractor[Int] does match numbers on the edge of Int's range") {
    Extractor[Int].extract(Int.MaxValue.toString) must equal (Some(Int.MaxValue))
    Extractor[Int].extract(Int.MinValue.toString) must equal (Some(Int.MinValue))
  }

  test("Extractor[Int] does match numbers within Int's range") {
    forAll { i: Int =>
      Extractor[Int].extract(i.toString) must equal (Some(i))
    }
  }

  test("Extractor[Long] doesn't match numbers out of Long's range") {
    forAll { i: Long =>
      whenever(i != 0) {
        if(i > 0) Extractor[Long].extract((BigInt(Long.MaxValue) + i).toString) must equal (None)
        else Extractor[Long].extract((BigInt(Long.MinValue) + i).toString) must equal (None)
      }
    }
  }

  test("Extractor[Long] does match numbers on the edge of Long's range") {
    Extractor[Long].extract(Long.MaxValue.toString) must equal (Some(Long.MaxValue))
    Extractor[Long].extract(Long.MinValue.toString) must equal (Some(Long.MinValue))
  }

  test("Extractor[Long] does match numbers within Long's range") {
    forAll { i: Long =>
      Extractor[Long].extract(i.toString) must equal (Some(i))
    }
  }
}
