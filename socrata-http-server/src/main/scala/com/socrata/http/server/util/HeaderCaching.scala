package com.socrata.http.server.util

import com.socrata.http.common.util.{HeaderParser, HttpHeaderParseException}
import scala.annotation.tailrec

sealed abstract class EntityTag {
  val value: String

  def weakCompare(that: EntityTag) = this.value == that.value
  def strongCompare(that: EntityTag): Boolean
}

case class WeakEntityTag(value: String) extends EntityTag {
  def strongCompare(that: EntityTag): Boolean = false
}

case class StrongEntityTag(value: String) extends EntityTag {
  def strongCompare(that: EntityTag): Boolean = that match {
    case StrongEntityTag(v) => value == v
    case WeakEntityTag(_) => false
  }
}

object EntityTag {
  def parse(headerParser: HeaderParser): Option[EntityTag] = try {
    if(headerParser.readLiteral("W/")) {
      Some(new WeakEntityTag(headerParser.readQuotedString()))
    } else {
      headerParser.tryReadQuotedString().map(StrongEntityTag)
    }
  } catch {
    case _: HttpHeaderParseException =>
      None
  }

  def parseList(headerParser: HeaderParser): Seq[EntityTag] = {
    val result = Vector.newBuilder[EntityTag]
    @tailrec
    def loop() {
      parse(headerParser) match {
        case Some(tag) =>
          result += tag
          if(headerParser.tryReadChar(',')) loop()
        case None =>
          // done
      }
    }
    loop()
    if(!headerParser.nothingLeft) throw new HttpHeaderParseException("Didn't consume entire header")
    result.result()
  }
}

object HeaderCaching {
  sealed trait IfNoneMatchResult
  sealed trait IfMatchResult
  case class ETags(etags: Seq[EntityTag]) extends IfNoneMatchResult with IfMatchResult
  case object IfNonexistant extends IfNoneMatchResult
  case object IfExists extends IfMatchResult
  case object Unparsable extends IfNoneMatchResult with IfMatchResult

  def parseIfNoneMatch(header: Option[String]): Option[IfNoneMatchResult] = header.map { s =>
    try {
      if(s.trim == "*") IfNonexistant
      else ETags(EntityTag.parseList(new HeaderParser(s)))
    } catch {
      case e: HttpHeaderParseException => Unparsable
    }
  }

  def parseIfMatch(header: Option[String]): Option[IfMatchResult] = header.map { s =>
    try {
      if(s.trim == "*") IfExists
      else ETags(EntityTag.parseList(new HeaderParser(s)))
    } catch {
      case e: HttpHeaderParseException => Unparsable
    }
  }
}
