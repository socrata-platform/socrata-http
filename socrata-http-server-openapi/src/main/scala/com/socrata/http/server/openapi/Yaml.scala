package com.socrata.http.server.openapi

import com.rojoma.json.v3.ast._

/** A minimal JSON -> YAML 1.2 renderer, sufficient for OpenAPI documents (block style,
  * every string value double-quoted).  No YAML library is needed because the input is
  * always an already-built `JValue` tree.
  */
object Yaml {
  def render(v: JValue): String = {
    val sb = new StringBuilder
    for(line <- lines(v)) sb.append(line).append('\n')
    sb.toString
  }

  private def isInline(v: JValue): Boolean = v match {
    case JObject(fields) => fields.isEmpty
    case JArray(elems) => elems.isEmpty
    case _ => true
  }

  private def inline(v: JValue): String = v match {
    case JString(s) => quote(s)
    case n: JNumber => n.toString
    case JBoolean(b) => b.toString
    case JNull => "null"
    case JObject(_) => "{}"
    case JArray(_) => "[]"
  }

  // The lines of a block collection (or a lone scalar), at indentation 0.
  private def lines(v: JValue): List[String] = v match {
    case JObject(fields) if fields.nonEmpty =>
      fields.toList.flatMap { case (k, value) =>
        if(isInline(value)) List(key(k) + ": " + inline(value))
        else (key(k) + ":") :: lines(value).map("  " + _)
      }
    case JArray(elems) if elems.nonEmpty =>
      elems.toList.flatMap { elem =>
        if(isInline(elem)) List("- " + inline(elem))
        else {
          val ls = lines(elem)
          ("- " + ls.head) :: ls.tail.map("  " + _)
        }
      }
    case other => List(inline(other))
  }

  private val plainKey = "[A-Za-z_][A-Za-z0-9_\\-]*".r
  // YAML 1.1 booleans/nulls, which some parsers still resolve for plain scalars
  private val reserved = Set("y", "n", "yes", "no", "true", "false", "on", "off", "null")

  private def key(k: String): String =
    if(plainKey.pattern.matcher(k).matches && !reserved(k.toLowerCase(java.util.Locale.US))) k
    else quote(k)

  private def quote(s: String): String = {
    val sb = new StringBuilder("\"")
    var i = 0
    while(i < s.length) {
      s.charAt(i) match {
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c if c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029 =>
          sb.append("\\u").append(f"${c.toInt}%04x")
        case c => sb.append(c)
      }
      i += 1
    }
    sb.append('"').toString
  }
}
