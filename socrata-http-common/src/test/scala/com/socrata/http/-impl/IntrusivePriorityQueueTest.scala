package com.socrata.http.`-impl`

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import org.scalatest.prop.PropertyChecks

class IntrusivePriorityQueueTest extends FunSuite with MustMatchers with PropertyChecks {
  class Node extends IntrusivePriorityQueueNode

  test("an empty queue raises NoSuchElementException when popped") {
    evaluating(new IntrusivePriorityQueue[Nothing].pop()) must produce[NoSuchElementException]
  }

  test("Adding elements produces a valid heap") {
    forAll { xs: List[Long] =>
      val pq = new IntrusivePriorityQueue[Node]
      for(x <- xs) pq.add(new Node, x)
      pq must be ('valid)
      pq.size must be (xs.length)
    }
  }

  test("inserted elements come out in order") {
    forAll { xs: List[Long] =>
      val pq = new IntrusivePriorityQueue[Node]
      for(x <- xs) pq.add(new Node, x)
      for(x <- xs.sorted) {
        pq.pop().priority must equal (x)
      }
      pq must be ('empty)
    }
  }

  test("removing an element from the middle of a queue leaves a valid queue") {
    forAll { (xs: List[Long], i: Int) =>
      whenever(xs.nonEmpty) {
        val idx = (i & 0x7fffffff) % xs.length
        val pq = new IntrusivePriorityQueue[Node]
        val nodes = xs.map { _ => new Node }
        for(node <- nodes) pq.add(node)
        pq.remove(nodes(idx)) must be (true)
        pq must be ('valid)
        pq.size must be (xs.length - 1)
      }
    }
  }
}
