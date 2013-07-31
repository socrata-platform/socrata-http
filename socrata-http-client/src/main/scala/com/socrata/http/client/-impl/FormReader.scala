package com.socrata.http.client.`-impl`

import java.io.Reader
import java.net.URLEncoder

private[client] class FormReader(values: Iterable[(String, String)]) extends Reader {
  private[this] val keysAndValues = values.iterator
  private[this] var remainingString: String = null
  private[this] var remainingStringOffset: Int = _
  private[this] var didOne = false

  private def stringify(kv: (String, String)): String = {
    val sb = new StringBuilder
    if(didOne) sb.append('&')
    sb.append(URLEncoder.encode(kv._1, "UTF-8"))
    sb.append('=')
    sb.append(URLEncoder.encode(kv._2, "UTF-8"))
    sb.toString
  }

  private def processToken(buf: Array[Char], offset: Int, length: Int): Int = {
    assert(remainingString != null)

    val toCopy = Math.min(length, remainingString.length - remainingStringOffset)
    val end = remainingStringOffset + toCopy
    remainingString.getChars(remainingStringOffset, end, buf, offset)
    if(remainingString.length == end) {
      if(keysAndValues.hasNext) {
        remainingString = stringify(keysAndValues.next())
        remainingStringOffset = 0
      } else {
        remainingString = null
      }
    } else {
      remainingStringOffset = end
    }

    toCopy
  }

  def read(buf: Array[Char], offset: Int, length: Int): Int = {
    if(length == 0) return 0

    if(remainingString == null) {
      if(!keysAndValues.hasNext) return -1

      remainingString = stringify(keysAndValues.next())
      remainingStringOffset = 0
      didOne = true
    }

    var wrote = 0
    do {
      wrote += processToken(buf, offset + wrote, length - wrote)
    } while(wrote != length && remainingString != null)

    wrote
  }

  def close() {}
}
