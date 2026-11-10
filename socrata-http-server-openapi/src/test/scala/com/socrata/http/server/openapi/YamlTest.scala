package com.socrata.http.server.openapi

import org.scalatest.{FunSuite, MustMatchers}
import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.interpolation._

class YamlTest extends FunSuite with MustMatchers {
  private def parse(yaml: String): JValue = YamlParsing.parse(yaml)

  test("scalars that look like other YAML types round-trip as strings") {
    val doc = JObject(Map(
      "values" -> JArray(Seq("true", "false", "null", "~", "yes", "no", "on", "123", "1.5", "1e3", "0x1F", "2024-01-01",
                             "- x", "a: b", "# not a comment", "&anchor", "*alias", "!tag", "%dir", "@at", "`tick",
                             "", " leading", "trailing ", "multi\nline", "tab\there", "quote\"s", "back\\slash",
                             "unicode é中😀", "nel\u0085", "ls ", "ctl\u0001\u007f").map(JString(_)))))
    parse(Yaml.render(doc)) must equal (doc)
  }

  test("keys that look like other YAML types round-trip as string keys") {
    val keys = Seq("200", "404", "true", "false", "null", "yes", "no", "on", "off", "y", "n", "1.5", "$ref", "x-foo", "a: b",
                   "/v1/widgets/{id}", "", " spaced", "with\"quote", "unicodeé")
    val doc = JObject(keys.map(_ -> JString("v")).toMap)
    parse(Yaml.render(doc)) must equal (doc)
  }

  test("nested structures, empty containers, numbers, booleans and null round-trip") {
    val doc = json"""{
      empty_obj: {}, empty_arr: [], n: 5, f: 1.5, neg: -3, t: true, fa: false, nothing: null,
      arr: [1, "two", [3, [4, []]], {}, {a: 1, b: [ {c: {d: "e"}}, [] ]}],
      deep: {a: {b: {c: {d: [ {e: "f"} ]}}}}
    }"""
    parse(Yaml.render(doc)) must equal (doc)
  }

  test("output is block style, with plain identifier keys and quoted strings") {
    Yaml.render(json"""{a: "b", c: [1, {d: "e"}], f: {}}""") must equal (
      """|a: "b"
         |c:
         |  - 1
         |  - d: "e"
         |f: {}
         |""".stripMargin)
  }
}
