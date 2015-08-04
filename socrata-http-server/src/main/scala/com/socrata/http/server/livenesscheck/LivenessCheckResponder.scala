package com.socrata.http.server.livenesscheck

import scala.util.Random

import java.nio.channels.spi.SelectorProvider
import java.nio.{BufferOverflowException, ByteBuffer}
import java.nio.channels.ClosedByInterruptException
import java.net.{InetAddress, InetSocketAddress}
import java.io.{Closeable, IOException}
import java.util.concurrent.CountDownLatch
import java.nio.charset.StandardCharsets

import com.rojoma.simplearm.v2._

import com.socrata.http.common.livenesscheck.LivenessCheckInfo

class LivenessCheckResponder(address: InetSocketAddress, rng: Random = new Random) extends Closeable {
  def this(config: LivenessCheckConfig) = {
    this(new InetSocketAddress(config.address.map(InetAddress.getByName).orNull, config.port.getOrElse(0)))
  }

  private val sendString = {
    val alphanum = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).mkString
    val sb = new StringBuilder
    for { _ <- 1 to 16 } sb.append(alphanum(rng.nextInt(alphanum.length)))
    sb.toString()
  }
  private val sendBytes = sendString.getBytes(StandardCharsets.UTF_8)
  private val log = org.slf4j.LoggerFactory.getLogger(classOf[LivenessCheckResponder])

  private val started = new CountDownLatch(1)
  @volatile private var port = 0
  @volatile private var problem: Option[Throwable] = None
  private var pingInfo: Option[LivenessCheckInfo] = None

  private val thread = new Thread() {
    setName(getId + " / Liveness check responder")
    override def run(): Unit = mainloop()
  }

  // runs the mainloop, responding to every ping packet it receives.
  //
  // The packets that it sends consist of the following:
  //   [the contents of the received ping] [the contents of `send`]
  //
  // At any time, this thread may be interrupted.  This will cause it to exit.
  private def mainloop(): Unit = {
    try {
      val selectorProvider = SelectorProvider.provider
      using(selectorProvider.openDatagramChannel()) { socket =>
        socket.bind(address)

        port = socket.getLocalAddress.asInstanceOf[InetSocketAddress].getPort
        started.countDown()

        val recvBufCap = 512
        val recvBuf = ByteBuffer.allocate(recvBufCap)
        while(true) {
          recvBuf.clear()
          val respondTo = socket.receive(recvBuf)
          try {
            log.trace("Ping from {}", respondTo)
            recvBuf.put(sendBytes).flip()
            socket.send(recvBuf, respondTo)
            log.trace("Pong!")
          } catch {
            case e: IOException =>
              log.trace("IO exception sending to the peer; ignoring")
            case e: BufferOverflowException =>
              log.trace("packet too large to add reply and still fit in 512 bytes; ignoring")
          }
        }
      }
    } catch {
      case _: InterruptedException | _: ClosedByInterruptException => // pass
      case e: Throwable =>
        problem = Some(e)
        started.countDown()
        log.error("Unexpected exception on pong thread", e)
        throw e
    }
  }

  def start(): Unit = {
    if (pingInfo.isDefined) throw new IllegalStateException("Already started")
    thread.start()
    started.await()
    problem.map(e => throw new Exception("Exception on pong thread", e)).getOrElse(())
    pingInfo = Some(new LivenessCheckInfo(port, sendString))
  }

  def livenessCheckInfo: LivenessCheckInfo = {
    pingInfo.getOrElse(throw new IllegalStateException("not yet started"))
  }

  def close(): Unit = {
    thread.interrupt()
    thread.join()
  }
}
