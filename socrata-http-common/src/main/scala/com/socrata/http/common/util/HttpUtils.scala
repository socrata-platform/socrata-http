package com.socrata.http.common.util

import com.rojoma.json.ast.JString
import java.lang.{StringBuilder => JStringBuilder}

class HttpHeaderParseException(msg: String) extends Exception(msg)

class HeaderParser(header: String) {
  private[this] var ptr = 0

  private def atEOF = ptr == header.length
  private def peek() = header.charAt(ptr)
  private def read() = {
    val result = header.charAt(ptr)
    ptr += 1
    result
  }
  private def skip() { ptr += 1 }

  def nothingLeft = {
    val oldPtr = header.length
    skipWhitespace()
    val res = atEOF
    ptr = oldPtr
    res
  }

  private def readTokenImpl(): String = {
    val sb = new java.lang.StringBuilder
    skipWhitespace()
    while(!atEOF && HttpUtils.isValidTokenChar(peek())) {
      sb.append(read())
    }
    if(sb.length == 0) throw new HttpHeaderParseException("No token found")
    sb.toString
  }

  def readToken(): String = {
    val origPtr = ptr
    try {
      readTokenImpl()
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  private def readQuotedStringImpl(): String = {
    // quoted-string  = ( <"> *(qdtext | quoted-pair ) <"> )
    // qdtext         = <any TEXT except <">>
    // quoted-pair    = "\" CHAR

    val sb = new java.lang.StringBuilder
    skipWhitespace()
    if(atEOF || read() != '"') throw new HttpHeaderParseException("Expected open-quote")
    while(!atEOF && peek() != '"') {
      val c = read()
      if(c == '\\') {
        if(atEOF) throw new HttpHeaderParseException("End of input in quoted-string after backslash")
        val c2 = read()
        if(!HttpUtils.isChar(c2)) {
          // ...this is a stupid reading, but I think it follows the letter of the HTTP spec
          // because "\" is a TEXT-that-is-not-<"> and therefore a valid qdtext.
          if(HttpUtils.isText(c2)) sb.append('\\').append(c2)
          else throw new HttpHeaderParseException("Expected char after backslash")
        } else sb.append(c2)
      } else if(HttpUtils.isText(c)) {
        sb.append(c)
      } else {
        throw new HttpHeaderParseException("Expected end quote, backslash, or text")
      }
    }
    if(atEOF) throw new HttpHeaderParseException("End of input in quoted-string")
    skip() // closing quote
    sb.toString
  }

  def readQuotedString(): String = {
    val origPtr = ptr
    try {
      readQuotedStringImpl()
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  private def tryReadQuotedStringImpl(): Option[String] = {
    skipWhitespace()
    if(atEOF || peek() != '"') None
    else Some(readQuotedStringImpl())
  }

  def tryReadQuotedString(): Option[String] = {
    val origPtr = ptr
    try {
      tryReadQuotedStringImpl()
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  private def readTokenOrQuotedStringImpl(): String = {
    skipWhitespace()
    if(atEOF) throw new HttpHeaderParseException("End of input expecting token or quoted-string")
    peek() match {
      case '"' => readQuotedStringImpl()
      case c if HttpUtils.isValidTokenChar(c) => readTokenImpl()
      case _ => throw new HttpHeaderParseException("Expected quote or token character")
    }
  }

  def readTokenOrQuotedString(): String = {
    val origPtr = ptr
    try {
      readTokenOrQuotedStringImpl()
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  private def skipWhitespace() {
    while(!atEOF && HttpUtils.isLws(peek())) skip()
  }

  private def readCharImpl(c: Char) {
    skipWhitespace()
    if(atEOF) { throw new HttpHeaderParseException("End of input while looking for " + c) }
    if(c != read()) { throw new HttpHeaderParseException("Expected " + c) }
  }

  def readChar(c: Char) {
    val origPtr = ptr
    try {
      readCharImpl(c)
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  private def readCharOrEOFImpl(c: Char): Boolean = {
    skipWhitespace()
    if(atEOF) false
    else if(c != read()) { throw new HttpHeaderParseException("Expected " + c) }
    else true
  }

  def readCharOrEOF(c: Char): Boolean = {
    val origPtr = ptr
    try {
      readCharOrEOFImpl(c)
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  def tryReadChar(c: Char): Boolean = {
    skipWhitespace()
    if(atEOF) false
    else if(peek() == c) { read(); true }
    else false
  }

  private def readLiteralImpl(lit: String): Boolean = {
    skipWhitespace()
    var i = 0
    while(i != lit.length) {
      if(peek() == lit.charAt(i)) { read() }
      else return false
      i += 1
    }
    true
  }

  def readLiteral(lit: String): Boolean = {
    val origPtr = ptr
    try {
      if(!readLiteralImpl(lit)) {
        ptr = origPtr
        false
      } else {
        true
      }
    } catch {
      case e: Throwable =>
        ptr = origPtr
        throw e
    }
  }

  def readLanguageRange(): Seq[String] = {
    skipWhitespace()
    // language-range  = ( ( 1*8ALPHA *( "-" 1*8ALPHA ) ) | "*" )
    if(atEOF) throw new HttpHeaderParseException("End of input while looking for language-range")
    if(peek() == '*') {
      skip()
      Vector.empty
    } else {
      val result = Vector.newBuilder[String]
      def readSeg() = {
        val sb = new java.lang.StringBuilder
        while(!atEOF && HttpUtils.isAlpha(peek())) sb.append(read())
        sb.toString
      }
      def readDash(): Boolean = {
        if(atEOF) false
        else if(peek() == '-') { skip(); true }
        else false
      }
      do {
        result += readSeg()
      } while(readDash())
      result.result()
    }
  }
}

sealed trait HttpUtils

object HttpUtils {
  private[this] val log = org.slf4j.LoggerFactory.getLogger(classOf[HttpUtils])

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

  def isChar(c: Char) = c < 128

  def isLws(c: Char) = c == ' ' || c == '\t'

  def isAlpha(c: Char) = ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')

  def isValidTokenChar(c: Char) =
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

  sealed trait Q {
    def q: Double
  }
  case class MediaRange(typ: String, subtyp: String, params: Seq[(String, String)], q: Double, acceptParams: Seq[(String, String)]) extends Q
  case class CharsetRange(charset: String, q: Double) extends Q
  case class LanguageRange(language: Seq[String], q: Double) extends Q

  private def parseParams(headerParser: HeaderParser): Vector[(String, String)] = {
    val b = Vector.newBuilder[(String, String)]
    while(headerParser.tryReadChar(';')) {
      val token = headerParser.readToken()
      val value = if(headerParser.tryReadChar('=')) {
        headerParser.readTokenOrQuotedString()
      } else ""
      b += (token -> value)
    }
    b.result()
  }

  def parseAccept(acceptHeader: String): Seq[MediaRange] = {
    // An "Accept" header is 0 or more of comma-separated media-ranges.
    // A media-range is ("*/*" or "type/*" or "type/subtype") followed by zero or more
    // ;attribute={token|quoted-string}
    // parameters.  The parameter "q" is special; it separates mimetype-parameters
    // from accept-parameters and must be a number from 0 to 1, with at most 3 places
    // after the (optional) decimal point.
    //
    // attribute, type, and subtype are all tokens.
    //
    // If a media-range has no "q" parameter it is assumed to be 1.
    //
    // Note: some broken user-agents will send "*" in place of "*/*'.
    try {
      val headerParser = new HeaderParser(acceptHeader)
      val mediaRanges = Vector.newBuilder[MediaRange]
      do {
        val typ = headerParser.readToken()
        val subtyp =
          if(headerParser.tryReadChar('/')) headerParser.readToken()
          else "*" // some broken clients send "*" instead of "*/*"
        val params = parseParams(headerParser)
        val qIdx = params.indexWhere(_._1.equalsIgnoreCase("q"))
        if(qIdx == -1) {
          mediaRanges += MediaRange(typ, subtyp, params, 1, Vector.empty)
        } else {
          val rawQValue = params(qIdx)._2
          val qValue = try {
            rawQValue.toDouble
          } catch {
            case e: NumberFormatException =>
              log.warn("Invalid qvalue: {}; treating it as 0", JString(rawQValue))
              0.0
          }
          mediaRanges += MediaRange(typ, subtyp, params.slice(0, qIdx), qValue, params.drop(qIdx + 1))
        }
      } while(headerParser.tryReadChar(','))
      mediaRanges.result()
    } catch {
      case e: HttpHeaderParseException =>
        log.warn("Malformed Accept header; ignoring it", e)
        Seq.empty
    }
  }

  def parseAcceptCharset(acceptCharsetHeader: String): Seq[CharsetRange] = {
    // An "Accept-Charset" header is 1 or more of comma-separated q-decorated charsets
    // or wildcards:
    //  ("*" | charset)[;q=qvalue]
    //
    // charset is a token.  qvalue is as above in `parseAccept`
    //
    // If a charset has no "q" parameter it is assumed to be 1.
    try {
      val headerParser = new HeaderParser(acceptCharsetHeader)
      val charsetRanges = Vector.newBuilder[CharsetRange]
      do {
        val charset = headerParser.readToken()
        val params = parseParams(headerParser)
        val qIdx = params.indexWhere(_._1.equalsIgnoreCase("q"))
        if(qIdx == -1) {
          charsetRanges += CharsetRange(charset, 1.0)
        } else {
          val rawQValue = params(qIdx)._2
          val qValue = try {
            rawQValue.toDouble
          } catch {
            case e: NumberFormatException =>
              log.warn("Invalid qvalue: {}; treating it as 0", JString(rawQValue))
              0.0
          }
          charsetRanges += CharsetRange(charset, qValue)
        }
      } while(headerParser.tryReadChar(','))
      charsetRanges.result()
    } catch {
      case e: HttpHeaderParseException =>
        log.warn("Malformed Accept-Charset header; ignoring it", e)
        Seq.empty
    }
  }

  def parseAcceptLanguage(acceptLanguageHeader: String): Seq[LanguageRange] = {
    // "Accept-Language" is much like Accept-Charset, only instead of "charset"
    // is has a language-range, which is [A-Za-z]{1,8}(-[A-Za-z]{1,8})*|\*
    // (i.e., a "*" or an unlimited number of dash-separated up-to-eight-letter blocks.)
    //
    // If a language-range has no "q" parameter it is assumed to be 1.
    try {
      val headerParser = new HeaderParser(acceptLanguageHeader)
      val languageRanges = Vector.newBuilder[LanguageRange]
      do {
        val languageRange = headerParser.readLanguageRange()
        val params = parseParams(headerParser)
        val qIdx = params.indexWhere(_._1.equalsIgnoreCase("q"))
        if(qIdx == -1) {
          languageRanges += LanguageRange(languageRange, 1.0)
        } else {
          val rawQValue = params(qIdx)._2
          val qValue = try {
            rawQValue.toDouble
          } catch {
            case e: NumberFormatException =>
              log.warn("Invalid qvalue: {}; treating it as 0", JString(rawQValue))
              0.0
          }
          languageRanges += LanguageRange(languageRange, qValue)
        }
      } while(headerParser.tryReadChar(','))
      languageRanges.result()
    } catch {
      case e: HttpHeaderParseException =>
        log.warn("Malformed Accept-Language header; ignoring it", e)
        Seq.empty
    }
  }
}
