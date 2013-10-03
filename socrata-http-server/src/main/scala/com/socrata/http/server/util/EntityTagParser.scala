package com.socrata.http.server.util

import com.socrata.http.common.util.{HttpHeaderParseException, HeaderParser}
import org.apache.commons.codec.binary.Base64
import scala.annotation.tailrec

object EntityTagParser {
  // this decodes using commons-codec's base64 decoder, which means it will silently ignore
  // non-base64 characters in etags.
  def parse(headerParser: HeaderParser): Option[EntityTag] =
    if(headerParser.tryReadLiteral("W/")) {
      Some(new WeakEntityTag(Base64.decodeBase64(headerParser.readQuotedString())))
    } else {
      headerParser.tryReadQuotedString().map { s => StrongEntityTag(Base64.decodeBase64(s)) }
    }

  def parse(etag: String, mustConsumeEntireInput: Boolean = true): EntityTag = {
    val hp = new HeaderParser(etag)
    parse(hp) match {
      case Some(result) =>
        if(!mustConsumeEntireInput || hp.nothingLeft) result
        else throw new HttpHeaderParseException("Did not consume entire input")
      case None =>
        throw new HttpHeaderParseException("Cannot parse ETag")
    }
  }

  def parseList(headerParser: HeaderParser): Seq[EntityTag] = {
    val result = Vector.newBuilder[EntityTag]
    @tailrec
    def loop() {
      parse(headerParser) match {
        case Some(tag) =>
          result += tag
          if(headerParser.readCharOrEOF(',')) loop()
        case None =>
          if(!headerParser.nothingLeft) throw new HttpHeaderParseException("Didn't consume entire header")
        // done
      }
    }
    loop()
    result.result()
  }

  def parseList(header: String): Seq[EntityTag] =
    parseList(new HeaderParser(header))
}
