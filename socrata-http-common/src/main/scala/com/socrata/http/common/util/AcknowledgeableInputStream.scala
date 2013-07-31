package com.socrata.http.common.util

import java.io.InputStream

class AcknowledgeableInputStream(underlying: InputStream, limit: Long) extends InputStream with Acknowledgeable {
  private var readSoFar: Long = 0L

  private def checkSize() {
    if(readSoFar > limit || readSoFar < 0) throw new TooMuchDataWithoutAcknowledgement
  }

  def acknowledge() {
    readSoFar = 0L
  }

  def read(): Int = {
    checkSize()
    val result = underlying.read()
    if(result >= 0) readSoFar += 1
    result
  }

  override def read(buf: Array[Byte], off: Int, len: Int): Int = {
    checkSize()
    val result = underlying.read(buf, off, len)
    if(result >= 0) readSoFar += result
    result
  }

  override def close() {
    underlying.close()
  }

  override def skip(n: Long): Long = {
    checkSize()
    val result = underlying.skip(Math.min(n, Int.MaxValue))
    readSoFar += result
    result
  }

  // mark/reset not supported
}
