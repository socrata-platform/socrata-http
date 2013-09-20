package com.socrata.http.server.util

import com.socrata.http.common.util.{HttpUtils, HeaderParser, HttpHeaderParseException}
import scala.annotation.tailrec
import javax.servlet.http.HttpServletRequest
import com.socrata.http.server.implicits._

sealed abstract class EntityTag {
  val value: String

  def weakCompare(that: EntityTag) = this.value == that.value
  def strongCompare(that: EntityTag): Boolean

  def map(f: String => String): EntityTag
}

case class WeakEntityTag(value: String) extends EntityTag {
  def strongCompare(that: EntityTag): Boolean = false
  def map(f: String => String) = WeakEntityTag(f(value))
  override def toString = "W/" + HttpUtils.quote(value)
}

case class StrongEntityTag(value: String) extends EntityTag {
  def strongCompare(that: EntityTag): Boolean = that match {
    case StrongEntityTag(v) => value == v
    case WeakEntityTag(_) => false
  }
  def map(f: String => String) = StrongEntityTag(f(value))
  override def toString = HttpUtils.quote(value)
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
  def parse(header: String): Option[EntityTag] = parse(new HeaderParser(header))

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
  def parseList(header: String): Seq[EntityTag] =
    parseList(new HeaderParser(header))
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
  def flatMap(f: EntityTag => Seq[EntityTag]) = {
    val newTags = etag.flatMap(f)
    if(newTags.isEmpty) IfDoesNotExist
    else IfNoneOf(newTags)
  }
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
  def flatMap(f: EntityTag => Seq[EntityTag]) = {
    val newTags = etag.flatMap(f)
    if(newTags.isEmpty) IfExists
    else IfAnyOf(newTags)
  }
}
case class AndPrecondition(a: Precondition, b: Precondition) extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Boolean =
    a.passes(tag, sideEffectFree) && b.passes(tag, sideEffectFree)

  def map(f: (EntityTag) => EntityTag): Precondition =
    AndPrecondition(a.map(f), b.map(f))

  def flatMap(f: (EntityTag) => Seq[EntityTag]): Precondition =
    AndPrecondition(a.flatMap(f), b.flatMap(f))
}

object Precondition {
  def parseETagList(s: String) = EntityTag.parseList(new HeaderParser(s))

  private def ifMatchPrecondition(req: HttpServletRequest): Option[Precondition] =
    req.header("If-Match") map { s =>
      if(s == "*") IfExists
      else IfAnyOf(parseETagList(s))
    }

  def precondition(req: HttpServletRequest): Precondition =
    req.header("If-None-Match") match {
      case Some(s) =>
        val inmPrecondition =
          if(s == "*") IfDoesNotExist
          else IfNoneOf(parseETagList(s))

        ifMatchPrecondition(req) match {
          case Some(imPrecondition) =>
            AndPrecondition(inmPrecondition, imPrecondition)
          case None =>
            inmPrecondition
        }
      case None =>
        ifMatchPrecondition(req).getOrElse(NoPrecondition)
    }
}
