package com.socrata.http.server.util

import org.scalatest.{Assertions, FunSuite}
import org.scalatest.matchers.MustMatchers
import org.scalatest.prop.PropertyChecks

class EntityTagTest extends FunSuite with MustMatchers with Assertions with PropertyChecks {
  test("startsWith matches whole") {
    assert(StrongEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,2,3,4)))
    assert(WeakEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,2,3,4)))
  }

  test("startsWith matches empty") {
    assert(StrongEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte]()))
    assert(WeakEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte]()))
  }

  test("startsWith matches partial") {
    assert(StrongEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,2)))
    assert(WeakEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,2)))
  }

  test("startsWith does not match overlong") {
    assert(!StrongEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,2,3,4,5)))
    assert(!WeakEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,2,3,4,5)))
  }

  test("startsWith does not match difference") {
    assert(!StrongEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,3)))
    assert(!WeakEntityTag(Array[Byte](1,2,3,4)).startsWith(Array[Byte](1,3)))
  }

  test("endsWith matches whole") {
    assert(StrongEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](1,2,3,4)))
    assert(WeakEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](1,2,3,4)))
  }

  test("endsWith matches empty") {
    assert(StrongEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte]()))
    assert(WeakEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte]()))
  }

  test("endsWith matches partial") {
    assert(StrongEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](3,4)))
    assert(WeakEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](3,4)))
  }

  test("endsWith does not match overlong") {
    assert(!StrongEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](1,2,3,4,5)))
    assert(!WeakEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](1,2,3,4,5)))
  }

  test("endsWith does not match difference") {
    assert(!StrongEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](1,3)))
    assert(!WeakEntityTag(Array[Byte](1,2,3,4)).endsWith(Array[Byte](1,3)))
  }

  test("EntityTag.appendBytes works") {
    forAll { (a: Array[Byte], b: Array[Byte]) =>
      EntityTag.appendBytes(a, b) must equal (a ++ b)
    }
  }

  test("EntityTag.dropBytes works") {
    forAll { (a: Array[Byte], n: Int) =>
      EntityTag.dropBytes(a, n) must equal (a.drop(n))
    }
  }

  test("EntityTag.dropRightBytes works") {
    forAll { (a: Array[Byte], n: Int) =>
      // can't just be
      //   EntityTag.dropRightBytes(a, n) must equal (a.dropRight(n))
      // because of an amusing bug in IndexedSeqOptimized#dropRight
      // when a.length - n overflows (due to n being near Int.MinValue)
      EntityTag.dropRightBytes(a, n) must equal (a.toList.dropRight(n).toArray)
    }
  }
}
