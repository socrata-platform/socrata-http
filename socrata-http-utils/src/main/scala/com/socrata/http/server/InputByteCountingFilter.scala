package com.socrata.http.server

import javax.servlet.http.{HttpServletRequestWrapper, HttpServletRequest}
import InputByteCountingFilter._
import io.Codec
import javax.servlet.ServletInputStream
import java.io._

import implicits._

trait InputByteCountingFilter extends SimpleFilter[HttpServletRequest, HttpResponse] {
  def apply(req: HttpServletRequest, service: HttpService): HttpResponse = {
    val wrapper = new CountingHttpServletRequest(req)
    service(wrapper) ~> (_ => read(wrapper.bytesRead))
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
      new ServletInputStreamFilter(getCountingInputStream)
    }

    override lazy val getReader: BufferedReader = {
      if(state == STREAM) throw new IllegalStateException("STREAM")
      state = READER
      new BufferedReader(new InputStreamReader(getCountingInputStream, Option(getCharacterEncoding).getOrElse(Codec.ISO8859.name)))
    }

    class ByteCountingInputStream(underlying: InputStream) extends FilterInputStream(underlying) {
      override def read(): Int = in.read() match {
        case -1 => -1
        case b => count += 1; b
      }

      override def read(buf: Array[Byte]): Int = in.read(buf) match {
        case -1 => -1
        case n => count += n; n
      }

      override def read(buf: Array[Byte], off: Int, len: Int): Int = in.read(buf, off, len) match {
        case -1 => -1
        case n => count += n; n
      }

      override def skip(n: Long) = {
        val skipped = in.skip(n)
        count += skipped
        skipped
      }
    }

    class ServletInputStreamFilter(underlying: InputStream) extends ServletInputStream {
      def read() = underlying.read()
      override def read(buf: Array[Byte]) = underlying.read(buf)
      override def read(buf: Array[Byte], off: Int, len: Int) = underlying.read(buf, off, len)
      override def skip(n: Long) = underlying.skip(n)
      override def markSupported = underlying.markSupported()
      override def mark(readLimit: Int) = underlying.mark(readLimit)
      override def reset() = underlying.reset()
      override def close() = underlying.close()
      override def available() = underlying.available()
    }

    def bytesRead = count
  }
}
