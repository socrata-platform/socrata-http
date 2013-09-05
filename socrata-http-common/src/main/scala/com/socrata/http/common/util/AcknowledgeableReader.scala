package com.socrata.http.common.util

import java.io.Reader

class AcknowledgeableReader(underlying: Reader, limit: Long) extends Reader with Acknowledgeable {
  private var readSoFar: Long = 0L

  private def checkSize() {
    if(readSoFar > limit || readSoFar < 0) throw new TooMuchDataWithoutAcknowledgement(limit)
  }

  def acknowledge() {
    readSoFar = 0L
  }

  override def read(): Int = {
    checkSize()
    val result = underlying.read()
    if(result >= 0) readSoFar += 1
    result
  }

  def read(cbuf: Array[Char], off: Int, len: Int): Int = {
    checkSize()
    val result = underlying.read(cbuf, off, len)
    if(result >= 0) readSoFar += result
    result
  }

  def close() {
    underlying.close()
  }
}
