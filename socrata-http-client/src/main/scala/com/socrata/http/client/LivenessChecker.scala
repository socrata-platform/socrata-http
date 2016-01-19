package com.socrata.http.client

import scala.collection.JavaConverters._
import java.io.{IOException, Closeable}
import java.nio.channels.spi.SelectorProvider
import scala.concurrent.duration.FiniteDuration
import java.net.{InetSocketAddress, InetAddress}
import scala.util.hashing.MurmurHash3
import java.util.concurrent.{Executor, ConcurrentHashMap, ConcurrentLinkedQueue}
import java.nio.ByteBuffer
import scala.annotation.tailrec
import scala.util.Random
import java.nio.channels.SelectionKey
import java.nio.charset.StandardCharsets
import com.socrata.http.`-impl`.{NoopCloseable, IntrusivePriorityQueueNode, IntrusivePriorityQueue}

// Note: "response" must not be mutated after being passed to this
final class LivenessCheckTarget private[client] (private [client] val host: InetAddress, private [client] val port: Int, private val response: Array[Byte]) {
  def this(host: InetAddress, port: Int, response: String) = this(host, port, response.getBytes(StandardCharsets.UTF_8))

  private val address = host.getAddress

  override val hashCode = MurmurHash3.bytesHash(response, MurmurHash3.bytesHash(address, port))

  override def equals(o: Any) = o match {
    case that: LivenessCheckTarget =>
      java.util.Arrays.equals(this.response, that.response)
    case _ => false
  }

  override lazy val toString =
    "LivenessCheckTarget(" + host + ", " + port + ", " + response.map("%02X" format _).mkString + ")"
}

trait LivenessChecker {
  def check(target: LivenessCheckTarget)(onFailure: => Unit): Closeable
}

object NoopLivenessChecker extends LivenessChecker {
  def check(target: LivenessCheckTarget)(onFailure: => Unit): Closeable = NoopCloseable
}

class InetLivenessChecker(interval: FiniteDuration, range: FiniteDuration, missable: Int, executor: Executor, rng: Random = new Random, bindPort: Int) extends LivenessChecker with Closeable {
  private val intervalMS = interval.toMillis
  private val rangeMS = range.toMillis

  require(intervalMS > 0, "interval")
  require(rangeMS > 0 && rangeMS <= Int.MaxValue, "range")
  require(missable >= 0, "missable")

  @volatile private var done = false
  @volatile private var impl: LivenessCheckerImpl = null

  def start() = synchronized {
    if(done || impl != null) throw new IllegalStateException("Already started")
    val x = new LivenessCheckerImpl(intervalMS, rangeMS.toInt, missable, executor, rng, bindPort)
    try {
      x.start()
    } catch {
      case e: Throwable =>
        x.closeSocketApparatus()
        throw e
    }
    impl = x
  }

  def close() = synchronized {
    if(!done) {
      done = true
      if(impl ne null) {
        impl.close()
        impl = null
      }
    }
  }

  def check(target: LivenessCheckTarget)(onFailure: => Unit): Closeable = {
    val result = new OnFailure(() => onFailure)
    impl.addJob(new PendingJob(target, result))
    result
  }
}

private[client] class PendingJob(val target: LivenessCheckTarget, val onFailure: OnFailure)

private[client] class OnFailure(val op: () => Unit) extends Runnable with Closeable {
  @volatile private var isCancelled: Boolean = false

  def cancelled = isCancelled

  def run() {
    if(!isCancelled) {
      op()
      close()
    }
  }

  private var removeFrom: ConcurrentHashMap[OnFailure, Any] = null

  def assignToJob(jobs: ConcurrentHashMap[OnFailure, Any]): Boolean = {
    synchronized {
      if(!isCancelled) {
        removeFrom = jobs
        removeFrom.put(this, this)
        true
      } else {
        false
      }
    }
  }

  def close() {
    isCancelled = true
    synchronized {
      if(removeFrom ne null) {
        removeFrom.remove(this)
        removeFrom = null
      }
    }
  }
}

private[client] final class LivenessCheckerImpl(intervalMS: Long, rangeMS: Int, missable: Int, executor: Executor, rng: Random, bindPort: Int) extends Thread {
  private val log = org.slf4j.LoggerFactory.getLogger(getClass)

  setName(getId + " / Liveness Checker")

  private val RandBytesSize = 16
  private val txPacketCapacity = 8 + RandBytesSize
  private val rxPacketHeader = txPacketCapacity
  val txPacket = ByteBuffer.allocate(txPacketCapacity)
  val rxPacket = ByteBuffer.allocate(512)

  private class Job(val target: LivenessCheckTarget) extends IntrusivePriorityQueueNode {
    def waitUntil = priority
    def waitUntil_=(msSinceEpoch: Long) = priority = msSinceEpoch

    override def toString = "job for " + target + " (" + onFailures.size + " listener(s))"

    waitUntil = System.currentTimeMillis()

    val socketAddress = new InetSocketAddress(target.host, target.port)

    val onFailures = new ConcurrentHashMap[OnFailure, Any] // really just want a ConcurrentHashSet

    var waiting = false // set to true after sending a ping; set to false on receipt of one
    var missed: Int = 0

    val me = new Array[Byte](RandBytesSize)
    rng.nextBytes(me)

    var counter = rng.nextLong()
    def fillTxPacket() {
      counter += 1
      val p = txPacket
      p.clear()
      p.putLong(counter)
      p.put(me)
      p.flip()
    }

    def isExpectedPacket(packet: ByteBuffer): Boolean = {
      packet.clear()
      if(packet.remaining < txPacketCapacity) return false
      if(packet.getLong() != counter) return false

      def checkBytes(bs: Array[Byte]): Boolean = {
        if(packet.remaining < bs.length) return false
        var i = 0
        while(i != bs.length && packet.get() == bs(i)) {
          i += 1
        }
        i == bs.length
      }

      if(!checkBytes(me)) return false
      true
    }
  }

  @volatile private var dead: Throwable = null
  private val pings = new java.util.HashMap[LivenessCheckTarget, Job]
  private val pingQueue = new IntrusivePriorityQueue[Job]

  private val newJobs = new ConcurrentLinkedQueue[PendingJob]()
  def addJob(job: PendingJob) {
    if(dead != null) throw new Exception("Unexpected exception in ping thread", dead)
    newJobs.add(job)
    selector.wakeup()
  }

  @volatile private var done = false
  private val provider = SelectorProvider.provider
  private val selector = provider.openSelector()
  private val socket = try {
    provider.openDatagramChannel()
  } catch {
    case e: Throwable =>
      selector.close()
      throw e
  }
  val selectionKey = try {
    socket.bind(new InetSocketAddress(bindPort))
    socket.configureBlocking(false)
    socket.register(selector, SelectionKey.OP_READ)
  } catch {
    case e: Throwable =>
      closeSocketApparatus()
      throw e
  }

  override def run() {
    try {
      while(!done) oneStep()
    } catch {
      case e: Throwable =>
        log.error("Unexpected exception escaped the ping thread!  I'm dying!")
        dead = e
    }
  }

  private def oneStep() {
    log.trace("{} distinct pingee(s) ", pingQueue.size)
    if(pingQueue.isEmpty) {
      log.trace("Zzzzzzzzzz....")
      try {
        selector.select()
      } catch {
        case e: IOException =>
          log.warn("Exception during unlimited select", e)
          return
      }
    } else {
      val head = pingQueue.head
      val timeout = head.waitUntil - System.currentTimeMillis()

      try {
        if(timeout <= 0) {
          log.trace("Checking socket; no time to sleep")
          selector.selectNow()
        } else {
          log.trace("Zzzz ({}ms)", timeout)
          selector.select(timeout)
        }
      } catch {
        case e: IOException =>
          log.warn("Exception during select", e)
      }
    }

    // there is only one key
    selector.selectedKeys.clear()

    startJobs()
    processPackets()
    clearHeadOfQueue()
  }

  private def processRxPacket(from: InetSocketAddress) {
    if(rxPacket.remaining < rxPacketHeader) {
      log.trace("Received a packet from {} smaller than the packet header; ignoring", from)
      return
    }

    val responseBytes = new Array[Byte](rxPacket.remaining - rxPacketHeader)
    rxPacket.position(rxPacketHeader)
    rxPacket.get(responseBytes)

    val job = pings.get(new LivenessCheckTarget(from.getAddress, from.getPort, responseBytes))
    if(job != null && !maybeDropJob(job)) {
      if(job.isExpectedPacket(rxPacket)) {
        log.trace("Received expected packet for {}", job)
        job.missed = 0
        job.waiting = false
      } else {
        log.warn("Received unexpected packet for {}; this probably means it's running very slowly", job)
      }
    } else {
      log.trace("Received a packet with no matching job")
    }
  }

  private def maybeDropJob(job: Job): Boolean = {
    if(job.onFailures.isEmpty) {
      log.trace("Dropping {} since it has no more failure-listeners", job)
      pings.remove(job.target)
      pingQueue.remove(job)
      true
    } else false
  }

  @tailrec
  private def processPackets() {
    rxPacket.clear()
    val from = try {
      socket.receive(rxPacket).asInstanceOf[InetSocketAddress]
    } catch {
      case e: IOException =>
        log.warn("Exception in receive; ignoring", e)
        return
    }
    if(from == null) return
    rxPacket.flip()
    log.debug("Received a {}-byte datagram from {}", rxPacket.limit, from)
    processRxPacket(from)
    processPackets()
  }

  private def clearHeadOfQueue() {
    val now = System.currentTimeMillis()
    while(pingQueue.nonEmpty && pingQueue.head.waitUntil <= now) {
      val job = pingQueue.head
      if(!maybeDropJob(job)) {
        if(job.waiting) {
          log.trace("Missed a packet for {}", job)
          missed(job)
        } else {
          sendPing(job)
        }
      }
    }
  }

  private def missed(job: Job) {
    assert(pingQueue.contains(job))
    job.missed += 1
    if(job.missed > missable) {
      log.trace("More than {} packets missed in a row by {}", missable, job)
      pings.remove(job.target)
      pingQueue.remove(job)
      for(f <- job.onFailures.keys.asScala) {
        if(!f.cancelled) executor.execute(f)
      }
    } else {
      sendPing(job)
    }
  }

  def sendPing(job: Job) {
    assert(pingQueue.contains(job))
    try {
      log.trace("Sending ping to {}", job.target)
      job.fillTxPacket()
      socket.send(txPacket, job.socketAddress)
    } catch {
      case e: IOException =>
        log.warn("Unexpected exception sending ping to {}; this will probably end up counting as missed.", job.target.asInstanceOf[Any], e.asInstanceOf[Any])
    }
    job.waitUntil += intervalMS + (rng.nextInt(rangeMS) - (rangeMS >> 1))
    job.waiting = true
  }

  private def startJobs() {
    def loop(nextJob: PendingJob) {
      if(nextJob != null) {
        startJob(nextJob)
        loop(newJobs.poll())
      }
    }
    loop(newJobs.poll())
  }

  private def startJob(job: PendingJob) {
    log.trace("Starting a job for {}", job.target)
    val existingJob = pings.get(job.target)
    if(existingJob == null) {
      log.trace("New job!")
      val newJob = new Job(job.target)
      if(job.onFailure.assignToJob(newJob.onFailures)) {
        pings.put(job.target, newJob)
        pingQueue.add(newJob)
      }
    } else {
      log.trace("Existing job!")
      job.onFailure.assignToJob(existingJob.onFailures)
    }
  }

  def closeSocketApparatus() {
    // TODO: catch exceptions
    socket.close()
    selector.close()
  }

  def close() {
    done = true
    selector.wakeup()

    // TODO: catch exceptions
    join()
    closeSocketApparatus()
  }
}




