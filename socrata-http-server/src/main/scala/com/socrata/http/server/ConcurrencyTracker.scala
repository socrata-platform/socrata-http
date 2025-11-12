package com.socrata.http.server

import java.util.concurrent.atomic.AtomicLong

class ConcurrencyTracker {
  private val pendingSubmissions = new AtomicLong(0)
  private val rejections = new AtomicLong(0)
  private val accepted = new AtomicLong(0)
  private val concurrency = new AtomicLong(0)

  def state = s"prac:${pendingSubmissions.get}/${rejections.get}/${accepted.get}/${concurrency}"

  def addSubmission(): Unit =
    pendingSubmissions.getAndIncrement()

  def rejectSubmission(): Unit = {
    rejections.getAndIncrement()
    pendingSubmissions.getAndDecrement()
  }

  def process[T](operation: => T): T = {
    pendingSubmissions.getAndDecrement()
    accepted.getAndIncrement()
    concurrency.getAndIncrement()
    try {
      operation
    } finally {
      concurrency.getAndDecrement()
    }
  }
}
