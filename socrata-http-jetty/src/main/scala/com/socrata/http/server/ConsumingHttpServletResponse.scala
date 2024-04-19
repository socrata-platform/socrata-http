package com.socrata.http.server

import java.io.{InputStream, Reader, IOException}
import jakarta.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServletResponseWrapper}

private class ConsumingHttpServletResponse(request: HttpServletRequest, val underlying: HttpServletResponse) extends HttpServletResponseWrapper(underlying) {
  def consume() {
    trait Consumer { def consume() }

    class InputStreamConsumer(stream: InputStream) extends Consumer {
      def consume() {
        val buf = new Array[Byte](4096)
        while(stream.read(buf) != -1) {}
      }
    }

    class ReaderConsumer(reader: Reader) extends Consumer {
      def consume() {
        val buf = new Array[Char](4096)
        while(reader.read(buf) != -1) {}
      }
    }

    try {
      val consumer =
        try {
          Some(new InputStreamConsumer(request.getInputStream))
        } catch {
          case _ : IllegalStateException =>
            try {
              Some(new ReaderConsumer(request.getReader))
            } catch {
              case _ : IllegalStateException =>
                None // uh... that shouldn't happen.  Guess we'll just not consume it then.
            }
        }

      consumer.foreach(_.consume())
    } catch {
      case _ : IOException =>
        // we'll just ignore this, because we're on our way out already
    }
  }

  override def getOutputStream = {
    consume()
    underlying.getOutputStream
  }

  override def getWriter = {
    consume()
    underlying.getWriter
  }

  override def sendError(code: Int) = {
    consume()
    underlying.sendError(code)
  }
}
