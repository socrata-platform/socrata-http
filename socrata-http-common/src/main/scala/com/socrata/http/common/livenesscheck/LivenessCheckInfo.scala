package com.socrata.http.common.livenesscheck

import scala.beans.BeanProperty

class LivenessCheckInfo(@BeanProperty var port: Int, @BeanProperty var response: String) {
  @deprecated(message = "This constructor is for Jackson's use, not yours", since = "forever")
  def this() = this(0, null)
}
