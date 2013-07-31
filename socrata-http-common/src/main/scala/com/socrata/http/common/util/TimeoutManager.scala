package com.socrata.http.common.util

import scala.collection.JavaConverters._

import java.io.Closeable
import java.util.concurrent.{Executor, TimeUnit}

import com.socrata.http.`-impl`.{IntrusivePriorityQueue, IntrusivePriorityQueueNode}

import TimeoutManager._

class TimeoutManager(executor: Executor) extends Closeable {
  private sealed trait PendingJob
  private case class CancelJob(job: Job) extends PendingJob
  private case class Job(onTimeout: () => Any, deadline: Long) extends IntrusivePriorityQueueNode with PendingJob with Closeable {
    priority = deadline
    def close() {
      pendingJobs.add(CancelJob(this))
    }
  }
  private object PoisonPill extends PendingJob

  private val worker = new Thread {
    override def run() {
      try {
        mainloop()
      } catch {
        case e: Throwable =>
          log.error("Unexpected exception", e)
          throw e
      }
    }
  }
  private val pendingJobs = new java.util.concurrent.LinkedBlockingQueue[PendingJob]

  def start() {
    worker.start()
  }

  def close() {
    pendingJobs.add(PoisonPill)
    worker.join()
  }

  private def mainloop() {
    val jobs = new IntrusivePriorityQueue[Job]

    while(true) {
      val now = System.currentTimeMillis()

      while(jobs.nonEmpty && jobs.head.deadline <= now) runJob(jobs.pop())

      val newJobs = new java.util.ArrayList[PendingJob]
      pendingJobs.drainTo(newJobs)
      if(newJobs.isEmpty) {
        if(jobs.isEmpty) {
          log.trace("Zzzzzz")
          newJobs.add(pendingJobs.take())
        } else {
          val delay = jobs.head.deadline - now
          log.trace("Zzzzzz ({}ms)", delay)
          val job = pendingJobs.poll(delay, TimeUnit.MILLISECONDS)
          if(job != null) newJobs.add(job)
        }
      }

      newJobs.asScala.foreach {
        case job: Job =>
          log.trace("Adding timeout at {}", job.deadline)
          jobs.add(job)
        case CancelJob(job) =>
          log.trace("Cancelling timeout at {}", job.deadline)
          jobs.remove(job)
        case PoisonPill =>
          if(jobs.nonEmpty) log.warn("Shutting down with " + jobs.size + " timeout jobs remaining")
          else log.trace("Shutting down")
          return
      }
    }
  }

  def addJob(timeout: Int)(onTimeout: => Any): Closeable = {
    val result = Job(() => onTimeout, System.currentTimeMillis() + timeout)
    pendingJobs.add(result)
    result
  }

  private def runJob(job: Job) {
    log.trace("Timeout expired")
    executor.execute(new Runnable {
      def run() { job.onTimeout() }
    })
  }
}

object TimeoutManager {
  val log = org.slf4j.LoggerFactory.getLogger(classOf[TimeoutManager])
}
