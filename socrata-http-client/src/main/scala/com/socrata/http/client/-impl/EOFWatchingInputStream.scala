package com.socrata.http.client.`-impl`

import java.io.{FilterInputStream, InputStream}

class EOFWatchingInputStream(underlying: InputStream) extends FilterInputStream(underlying) {
  private var sawEOF = false

  override def read(): Int = {
    val result = super.read()
    if(result == -1) sawEOF = true
    result
  }

  override def read(bs: Array[Byte]): Int = {
    val result = super.read(bs)
    if(result == -1) sawEOF = true
    result
  }

  override def read(bs: Array[Byte], offs: Int, len: Int): Int = {
    val result = super.read(bs, offs, len)
    if(result == -1) sawEOF = true
    result
  }

  def eofReached = sawEOF
}
