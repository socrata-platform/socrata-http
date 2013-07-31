package com.socrata.http.`-impl`

import java.io.Closeable

private[http] object NoopCloseable extends Closeable {
  def close() {}
}
