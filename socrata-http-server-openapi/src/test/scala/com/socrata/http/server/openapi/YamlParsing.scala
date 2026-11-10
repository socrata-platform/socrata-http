package com.socrata.http.server.openapi

import scala.collection.JavaConverters._

import com.rojoma.json.v3.ast._
import org.yaml.snakeyaml.{Yaml => SnakeYaml}

object YamlParsing {
  /** Parses YAML with a real parser and converts to JValue.  Any non-String map key throws, which is
    * how tests check that numeric-looking keys like "200" were quoted.
    */
  def parse(yaml: String): JValue = {
    def conv(x: Any): JValue = x match {
      case null => JNull
      case m: java.util.Map[_, _] => JObject(m.asScala.map {
        case (k: String, v) => k -> conv(v)
        case (k, _) => throw new AssertionError("non-string YAML key " + k + " of " + k.getClass)
      }.toMap)
      case l: java.util.List[_] => JArray(l.asScala.map(conv).toSeq)
      case s: String => JString(s)
      case b: java.lang.Boolean => JBoolean(b)
      case i: java.lang.Integer => JNumber(i.intValue)
      case l: java.lang.Long => JNumber(l.longValue)
      case d: java.lang.Double => JNumber(d.doubleValue)
      case other => throw new AssertionError("unexpected YAML value " + other + " of " + other.getClass)
    }
    conv(new SnakeYaml().load[Object](yaml))
  }
}
