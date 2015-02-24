package com.socrata.http.common

import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.jackson.codehaus.{JObjectDeserializer, JObjectSerializer}
import com.socrata.http.common.livenesscheck.LivenessCheckInfo
import org.codehaus.jackson.map.annotate.{JsonDeserialize, JsonSerialize}

/** A class for storing in a curator service advertisement's payload field.
  * All fields of this class must be (de)serializable by Jackson. */
class AuxiliaryData(var livenessCheckInfo: Option[LivenessCheckInfo], var json: JObject) {
  def this(livenessCheckInfo: Option[LivenessCheckInfo]) = this(livenessCheckInfo, JObject.canonicalEmpty)

  @deprecated(message = "This constructor is for Jackson's use, not yours", since = "forever")
  def this() = this(None, JObject.canonicalEmpty)

  // can't use @BeanProperty because of the Option
  def getLivenessCheckInfo = livenessCheckInfo.orNull
  def setLivenessCheckInfo(lci: LivenessCheckInfo) { livenessCheckInfo = Option(lci) }

  @JsonSerialize(using = classOf[JObjectSerializer])
  def getJson = json

  @JsonDeserialize(using = classOf[JObjectDeserializer])
  def setJson(newJson: JObject) {
    if(newJson eq null) json = JObject.canonicalEmpty
    else json = newJson
  }
}
