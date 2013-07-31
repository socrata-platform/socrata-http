package com.socrata.http.common

import com.socrata.http.common.livenesscheck.LivenessCheckInfo

/** A class for storing in a curator service advertisement's payload field.
  * All fields of this class must be (de)serializable by Jackson. */
class AuxiliaryData(var livenessCheckInfo: Option[LivenessCheckInfo]) {
  @deprecated(message = "This constructor is for Jackson's use, not yours", since = "forever")
  def this() = this(None)

  // can't use @BeanProperty because of the Option
  def getLivenessCheckInfo = livenessCheckInfo.orNull
  def setLivenessCheckInfo(lci: LivenessCheckInfo) { livenessCheckInfo = Option(lci) }
}
