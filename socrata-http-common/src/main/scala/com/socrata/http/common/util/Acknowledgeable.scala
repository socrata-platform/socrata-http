package com.socrata.http.common.util

import java.io.IOException

trait Acknowledgeable {
  def acknowledge()
}

class TooMuchDataWithoutAcknowledgement(val limit: Long) extends IOException("Too much data without acknowledgement")
