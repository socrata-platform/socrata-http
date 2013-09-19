package com.socrata.http.server.util

import com.socrata.http.common.util.{HeaderParser, HttpHeaderParseException}
import scala.annotation.tailrec
import javax.servlet.http.HttpServletRequest
import com.socrata.http.server.implicits._

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

sealed abstract class Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean
  def map(f: EntityTag => EntityTag): Precondition
  def flatMap(f: EntityTag => Seq[EntityTag]): Precondition
}
case object NoPrecondition extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean = true
  def map(f: EntityTag => EntityTag) = this
  def flatMap(f: EntityTag => Seq[EntityTag]) = this
}
case object IfDoesNotExist extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean = tag.isEmpty
  def map(f: EntityTag => EntityTag) = this
  def flatMap(f: EntityTag => Seq[EntityTag]) = this
}
case class IfNoneOf(etag: Seq[EntityTag]) extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean = tag match {
    case Some(t) =>
      if(sideEffectFree) !etag.exists(_.weakCompare(t))
      else !etag.exists(_.strongCompare(t))
    case None =>
      true
  }
  def map(f: EntityTag => EntityTag) = IfNoneOf(etag.map(f))
  def flatMap(f: EntityTag => Seq[EntityTag]) = IfNoneOf(etag.flatMap(f))
}
case object IfExists extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean = tag.nonEmpty
  def map(f: EntityTag => EntityTag) = this
  def flatMap(f: EntityTag => Seq[EntityTag]) = this
}
case class IfAnyOf(etag: Seq[EntityTag]) extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean = tag match {
    case Some(t) =>
      etag.exists(_.strongCompare(t))
    case None =>
      true
  }
  def map(f: EntityTag => EntityTag) = IfAnyOf(etag.map(f))
  def flatMap(f: EntityTag => Seq[EntityTag]) = IfAnyOf(etag.flatMap(f))
}

object Precondition {
  def parseETagList(s: String) = EntityTag.parseList(new HeaderParser(s))

  def precondition(req: HttpServletRequest): Precondition =
    req.header("If-None-Match") match {
      case Some(s) =>
        // result of having both if-match and if-none-match is undefined, so we'll just
        // arbitrarily prefer I-N-M if both exist.
        if(s == "*") IfDoesNotExist
        else IfNoneOf(parseETagList(s))
      case None =>
        req.header("If-Match") match {
          case Some(s) =>
            if(s == "*") IfExists
            else IfAnyOf(parseETagList(s))
          case None =>
            NoPrecondition
        }
    }
}
