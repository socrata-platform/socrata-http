package com.socrata.http.client.`-impl`

import com.rojoma.json.v3.io.{EventTokenIterator, JsonEvent}
import java.io.Reader

private[client] class JsonEventIteratorReader(events: Iterator[JsonEvent]) extends Reader {
  private[this] val tokens = EventTokenIterator(events)
  private[this] var remainingToken: String = null
  private[this] var remainingTokenOffset: Int = _

  private def processToken(buf: Array[Char], offset: Int, length: Int): Int = {
    assert(remainingToken != null)

    val toCopy = Math.min(length, remainingToken.length - remainingTokenOffset)
    val tokenEnd = remainingTokenOffset + toCopy
    remainingToken.getChars(remainingTokenOffset, tokenEnd, buf, offset)
    if(remainingToken.length == tokenEnd) {
      if(tokens.hasNext) {
        remainingToken = tokens.next().asFragment
        remainingTokenOffset = 0
      } else {
        remainingToken = null
      }
    } else {
      remainingTokenOffset = tokenEnd
    }

    toCopy
  }

  def read(buf: Array[Char], offset: Int, length: Int): Int = {
    if(length == 0) return 0

    if(remainingToken == null) {
      if(!tokens.hasNext) return -1

      remainingToken = tokens.next().asFragment
      remainingTokenOffset = 0
    }

    var wrote = 0
    do {
      wrote += processToken(buf, offset + wrote, length - wrote)
    } while(wrote != length && remainingToken != null)

    wrote
  }

  def close() {}
}
