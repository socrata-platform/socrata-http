package com.socrata.http.internal


trait Throttler {
  def permitAccess(token:String):Boolean
}

case class CMSThrottler(counter:TimeWindowedCountMinSketch, limit:Int) extends Throttler {
  override def permitAccess(token:String) = {
    counter.increment(token)
    val counts = counter.count(token)
    counts <= limit
  }
}
