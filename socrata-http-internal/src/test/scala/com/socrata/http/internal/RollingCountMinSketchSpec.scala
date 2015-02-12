package com.socrata.http.internal

import org.scalatest.{ParallelTestExecution, WordSpec, ShouldMatchers}

class RollingCountMinSketchSpec extends WordSpec with ShouldMatchers with ParallelTestExecution {
  "The rolling counter" should {
    "count things in a single bucket without advancing appropriately" in {
      val counter = new RollingCountMinSketch(1)
      counter.increment("a")
      counter.increment("b")
      counter.increment("a")
      counter.count("a") should be (2)
      counter.count("b") should be (1)
    }
    "count things in two buckets when advancing appropriately" in {
      val counter = new RollingCountMinSketch(2)
      counter.increment("a")
      counter.increment("b")
      counter.nextWindow()
      counter.increment("a")
      counter.count("a") should be (2)
      counter.count("b") should be (1)
    }

    "count things as you roll the window past the endpoint" in {
      val counter = new RollingCountMinSketch(2)
      counter.increment("a")
      counter.increment("b")
      counter.nextWindow()
      counter.increment("a")
      counter.increment("b")
      counter.nextWindow()
      counter.count("a") should be (1)
      counter.count("b") should be (1)
    }

  }

}
