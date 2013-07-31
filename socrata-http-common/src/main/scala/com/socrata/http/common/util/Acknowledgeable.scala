package com.socrata.http.common.util

import java.io.IOException

trait Acknowledgeable {
  def acknowledge()
}

class TooMuchDataWithoutAcknowledgement extends IOException("Too much data without acknowledgement")
