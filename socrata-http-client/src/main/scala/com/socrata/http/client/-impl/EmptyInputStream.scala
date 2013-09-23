package com.socrata.http.client.`-impl`

import java.io.InputStream

object EmptyInputStream extends InputStream {
  def read(): Int = -1
}
