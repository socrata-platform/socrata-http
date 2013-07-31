package com.socrata.http.client.`-impl`

import java.io.InputStream

private[client] class CatchingInputStream private (underlying: InputStream, onException: PartialFunction[Throwable, Nothing]) extends InputStream {
  @inline
  private def maybeReceiveTimeout[T](f: => T): T =
    try {
      f
    } catch onException

  def read(): Int =
    maybeReceiveTimeout(underlying.read())

  override def read(bs: Array[Byte]): Int =
    maybeReceiveTimeout(underlying.read(bs))

  override def read(bs: Array[Byte], off: Int, len: Int): Int =
    maybeReceiveTimeout(underlying.read(bs, off, len))

  override def skip(n: Long): Long =
    maybeReceiveTimeout(underlying.skip(n))

  override def available: Int =
    maybeReceiveTimeout(underlying.available)

  override def mark(n: Int) =
    maybeReceiveTimeout(underlying.mark(n))

  override def reset() =
    maybeReceiveTimeout(underlying.reset())

  override def close() =
    maybeReceiveTimeout(underlying.close())
}

private[client] object CatchingInputStream {
  def apply(underlying: InputStream)(onException: PartialFunction[Throwable, Nothing]) =
    new CatchingInputStream(underlying, onException)
}
