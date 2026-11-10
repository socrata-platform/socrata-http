package com.socrata.http.server.openapi

import com.rojoma.json.v3.ast._

/** A (very) thin wrapper around a JSON Schema fragment.  Use the helpers in the
  * companion for the common cases, or `Schema.raw` to supply any schema you like.
  */
final case class Schema(json: JObject) {
  def withDescription(d: String): Schema = Schema(JObject(json.fields + ("description" -> JString(d))))
  def withExample(v: JValue): Schema = Schema(JObject(json.fields + ("example" -> v)))
  def withEnum(values: String*): Schema = Schema(JObject(json.fields + ("enum" -> JArray(values.map(JString(_))))))
}

object Schema {
  private def typed(t: String, format: Option[String] = None) =
    Schema(JObject(Map("type" -> JString(t)) ++ format.map("format" -> JString(_))))

  val string: Schema = typed("string")
  val integer: Schema = typed("integer", Some("int32"))
  val long: Schema = typed("integer", Some("int64"))
  val number: Schema = typed("number")
  val boolean: Schema = typed("boolean")
  val anyObject: Schema = typed("object")

  def array(items: Schema): Schema = Schema(JObject(Map("type" -> JString("array"), "items" -> items.json)))

  def obj(required: Set[String], properties: (String, Schema)*): Schema = {
    val base = Map[String, JValue](
      "type" -> JString("object"),
      "properties" -> JObject(properties.map { case (k, v) => k -> v.json }.toMap))
    Schema(JObject(if(required.isEmpty) base else base + ("required" -> JArray(required.toSeq.sorted.map(JString(_))))))
  }

  /** A reference to a schema registered under `components.schemas`. */
  def ref(name: String): Schema = Schema(JObject(Map("$ref" -> JString("#/components/schemas/" + name))))

  def raw(json: JObject): Schema = Schema(json)
}
