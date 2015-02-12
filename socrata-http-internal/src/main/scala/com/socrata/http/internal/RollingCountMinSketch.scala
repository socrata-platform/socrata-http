package com.socrata.http.internal

import java.util.concurrent.{TimeUnit, Executors}

import com.clearspring.analytics.stream.frequency.CountMinSketch

import scala.util.Random

class TimeWindowedCountMinSketch(windows:Int, duration:Long) {
  val rollingCMS = new RollingCountMinSketch(windows)

  private val scheduledThreadPool = Executors.newScheduledThreadPool(1)
  scheduledThreadPool.scheduleWithFixedDelay(new HeartBeat(rollingCMS), duration, duration, TimeUnit.MILLISECONDS)

  def increment(token:String) = rollingCMS.increment(token)

  def count(token:String) = rollingCMS.count(token)

  class HeartBeat(counter: RollingCountMinSketch) extends Runnable {
    def run() = {
      counter.nextWindow()
    }
  }



  def stop() = {
    scheduledThreadPool.shutdown
    scheduledThreadPool.awaitTermination(duration,TimeUnit.MILLISECONDS)
  }
}

class RollingCountMinSketch(windows:Int) {
  val sketches = Array.fill(windows)(new CountMinSketch(4, 10, 3451))

  // The current bucket update is NOT threadsafe
  var currentBucket = 0

  def increment(token:String):Unit = {
    sketches(currentBucket).add(token,1)
  }

  def count(token:String) = {
   CountMinSketch.merge(sketches : _*).estimateCount(token)
  }

  def nextWindow() = {
    currentBucket = ( currentBucket + 1 ) % windows
    sketches(currentBucket) = new CountMinSketch(4,10,3451)
  }


}