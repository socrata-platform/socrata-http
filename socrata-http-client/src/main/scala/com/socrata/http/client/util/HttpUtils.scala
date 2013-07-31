package com.socrata.http.client.util

import com.rojoma.json.ast.JString
import java.lang.{StringBuilder => JStringBuilder}

object HttpUtils {
  def isToken(s: String): Boolean = {
    if(s.isEmpty) return false

    var i = 0
    do {
      if(!isValidTokenChar(s.charAt(i))) return false
      i += 1
    } while(i != s.length)

    true
  }

  /** Produces a quoted-string.
    *
    * @throws IllegalArgumentException if s contains invalid characters.  The StringBuilder
    *                                  may have been appended to, but the changes will have
    *                                  been rolled back via `setLength`.
    */
  def quoteInto(sb: JStringBuilder, s: String): JStringBuilder = {
    val origLen = sb.length

    sb.append('"')

    var i = 0
    while(i != s.length) {
      val c = s.charAt(i)
      if(isText(c)) {
        if(c == '"') sb.append("\\\"")
        else if(c == '\\') sb.append("\\\\")
        else sb.append(c)
      } else {
        sb.setLength(origLen)
        throw new IllegalArgumentException("Not a valid character for a quoted-string: " + JString(c.toString))
      }
      i += 1
    }

    sb.append('"')
  }

  def quote(s: String): String =
    quoteInto(new JStringBuilder, s).toString

  def isText(c: Char) = {
    c < 256 && (!isCtl(c) || c == ' ' || c == '\t')
  }

  def isLws(c: Char) = c == ' ' || c == '\t'

  private def isValidTokenChar(c: Char) =
    !isCtl(c) && !isSeparator(c) && c < 256

  def isCtl(c: Char) =
    c < 32 || c == 127

  private[this] val separators = locally {
    val arr = new Array[Boolean](128)
    for(c <- "()<>@,;:\\\"/[]?={} \t") arr(c) = true
    arr
  }

  def isSeparator(c: Char) =
    c < 128 && separators(c)
}
