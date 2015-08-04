package com.socrata.http.server.`-impl`

import java.io.{IOException, PrintWriter, Writer}

class TroubleUnignoringWriter(underlying: PrintWriter) extends Writer {
  private def checkError(): Unit = {
    if(underlying.checkError()) throw new IOException("HTTP servlet response writer caught and ignored an IO error")
  }

  override def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
    underlying.write(cbuf, off, len)
    checkError()
  }

  override def flush(): Unit = {
    underlying.flush()
    checkError()
  }

  override def close(): Unit = {
    underlying.close()
    checkError()
  }
}
