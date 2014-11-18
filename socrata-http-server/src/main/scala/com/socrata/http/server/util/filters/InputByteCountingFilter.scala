package com.socrata.http.server.util.filters

import javax.servlet.http.{HttpServletRequestWrapper, HttpServletRequest}
import InputByteCountingFilter._
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest
import io.Codec
import javax.servlet.ServletInputStream
import java.io._

import com.socrata.http.server._
import com.socrata.http.server.implicits._

trait InputByteCountingFilter extends SimpleFilter[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest, service: HttpService): HttpResponse = {
    val servletRequestWrapper = new CountingHttpServletRequest(req.servletRequest)
    val wrapper = new WrapperHttpRequest(req) {
      override def servletRequest = new AugmentedHttpServletRequest(servletRequestWrapper)
    }
    service(wrapper) ~> (_ => read(servletRequestWrapper.bytesRead))
  }

  def read(bytes: Long)
}

object InputByteCountingFilter {
  class CountingHttpServletRequest(underlying: HttpServletRequest) extends HttpServletRequestWrapper(underlying) {
    private val NONE = 0
    private val READER = 1
    private val STREAM = 2

    private var state = NONE

    private var count = 0L

    private def getCountingInputStream = new ByteCountingInputStream(super.getInputStream)

    override lazy val getInputStream: ServletInputStream = {
      if(state == READER) throw new IllegalStateException("READER")
      state = STREAM
      getCountingInputStream
    }

    override lazy val getReader: BufferedReader = {
      if(state == STREAM) throw new IllegalStateException("STREAM")
      state = READER
      new BufferedReader(new InputStreamReader(getCountingInputStream, Option(getCharacterEncoding).getOrElse(Codec.ISO8859.name)))
    }

    class ByteCountingInputStream(underlying: ServletInputStream) extends ServletInputStream {
      override def read(): Int = underlying.read() match {
        case -1 => -1
        case b => count += 1; b
      }

      override def read(buf: Array[Byte]): Int = underlying.read(buf) match {
        case -1 => -1
        case n => count += n; n
      }

      override def read(buf: Array[Byte], off: Int, len: Int): Int = underlying.read(buf, off, len) match {
        case -1 => -1
        case n => count += n; n
      }

      override def skip(n: Long) = {
        val skipped = underlying.skip(n)
        count += skipped
        skipped
      }

      override def markSupported = underlying.markSupported()
      override def mark(readLimit: Int) = underlying.mark(readLimit)
      override def reset() = underlying.reset()
      override def close() = underlying.close()
      override def available() = underlying.available()

      def isFinished: Boolean = underlying.isFinished
      def isReady(): Boolean = underlying.isReady
      def setReadListener(x: javax.servlet.ReadListener) = underlying.setReadListener(x)
    }

    def bytesRead = count
  }
}
