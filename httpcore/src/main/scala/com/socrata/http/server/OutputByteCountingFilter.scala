package com.socrata.http.server

import OutputByteCountingFilter._
import javax.servlet.ServletOutputStream
import java.io._
import javax.servlet.http.{HttpServletResponse, HttpServletResponseWrapper, HttpServletRequest}

trait OutputByteCountingFilter extends SimpleFilter[HttpServletRequest, HttpResponse] {
  def apply(req: HttpServletRequest, service: HttpService): HttpResponse = {
    val script = service(req)

    { resp =>
      val wrapper = new CountingHttpServletResponse(resp)
      script(wrapper)
      wrote(wrapper.bytesWritten)
    }
  }

  def wrote(bytes: Long)
}

object OutputByteCountingFilter {
  class CountingHttpServletResponse(underlying: HttpServletResponse) extends HttpServletResponseWrapper(underlying) {
    private val NONE = 0
    private val WRITER = 1
    private val STREAM = 2

    private var state = NONE

    private var count = 0L

    def getCountingOutputStream = new ByteCountingOutputStream(super.getOutputStream)

    override lazy val getOutputStream: ServletOutputStream = {
      if(state == WRITER) throw new IllegalStateException("WRITER")
      state = STREAM
      new ServletOutputStreamFilter(getCountingOutputStream)
    }

    // This is not actually possible to implement correctly without re-doing vast amounts of terribly hairy
    // mime and charset logic in Jetty's Response class.  So we'll punt and assume that the output's in
    // UTF-8, which is will be almost all the time.  We'll further assume that most of the time we're within
    // the base multilingual plane, which will also be right most of the time. It'll be in the right ballpark
    // anyway.
    override lazy val getWriter: PrintWriter = {
      if(state == STREAM) throw new IllegalStateException("STREAM")
      state = WRITER

      new PrintWriter(new UTF8CountingWriter(super.getWriter))
    }

    class ByteCountingOutputStream(underlying: OutputStream) extends FilterOutputStream(underlying) {
      override def write(b: Int) {
        underlying.write(b)
        count += 1
      }

      override def write(bs: Array[Byte]) {
        underlying.write(bs)
        count += bs.length
      }

      override def write(bs: Array[Byte], off: Int, len: Int) {
        underlying.write(bs, off, len)
        count += len
      }

      def bytesWritten = count
    }

    class ServletOutputStreamFilter(underlying: OutputStream) extends ServletOutputStream {
      def write(b: Int) = underlying.write(b)
      override def write(bs: Array[Byte]) = underlying.write(bs)
      override def write(bs: Array[Byte], off: Int, len: Int) = underlying.write(bs, off, len)
    }

    class UTF8CountingWriter(underlying: Writer) extends FilterWriter(underlying) {
      override def write(c: Int) {
        super.write(c)
        count += utf8length(c.toChar)
      }

      override def write(cbuf: Array[Char], off: Int, len: Int) {
        super.write(cbuf, off, len)

        var i = 0
        while(i != len) {
          count += utf8length(cbuf(off + i))
          i += 1
        }
      }

      override def write(s: String, off: Int, len: Int) {
        super.write(s, off, len)

        var i = 0;
        while(i != len) {
          count += utf8length(s.charAt(i + off))
          i += 1
        }
      }

      def utf8length(c: Char): Int = {
        if(c < 0x80) 1
        else if(c < 0x8000) 2
        else 3
      }
    }

    def bytesWritten = count
  }
}
