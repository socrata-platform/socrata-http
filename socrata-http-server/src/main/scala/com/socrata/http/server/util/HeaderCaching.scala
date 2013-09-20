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
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result
  def filter(f: EntityTag => Boolean): Option[Precondition]
  def map(f: EntityTag => EntityTag): Precondition
}
case object NoPrecondition extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean) = Precondition.Passed
  def filter(f: EntityTag => Boolean) = Some(this)
  def map(f: EntityTag => EntityTag) = this
}
case object IfDoesNotExist extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean) = if(tag.isEmpty) Precondition.FailedBecauseMatch else Precondition.Passed
  def filter(f: EntityTag => Boolean) = Some(this)
  def map(f: EntityTag => EntityTag) = this
}
case class IfNoneOf(etag: Seq[EntityTag]) extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean) = tag match {
    case Some(t) =>
      val matches =
        if(sideEffectFree) etag.exists(_.weakCompare(t))
        else etag.exists(_.strongCompare(t))
      if(matches) Precondition.FailedBecauseMatch
      else Precondition.Passed
    case None =>
      Precondition.Passed
  }
  def filter(f: EntityTag => Boolean) = {
    val newTags = etag.filter(f)
    if(newTags.isEmpty) Some(NoPrecondition)
    else Some(IfNoneOf(newTags))
  }
  def map(f: EntityTag => EntityTag) = IfNoneOf(etag.map(f))
}
case object IfExists extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean) = if(tag.nonEmpty) Precondition.FailedBecauseNoMatch else Precondition.Passed
  def filter(f: EntityTag => Boolean) = Some(this)
  def map(f: EntityTag => EntityTag) = this
}
case class IfAnyOf(etag: Seq[EntityTag]) extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean) = tag match {
    case Some(t) =>
      if(etag.exists(_.strongCompare(t))) Precondition.Passed
      else Precondition.FailedBecauseNoMatch
    case None =>
      Precondition.Passed
  }
  def map(f: EntityTag => EntityTag) = IfAnyOf(etag.map(f))
  def filter(f: EntityTag => Boolean) = {
    val newTags = etag.filter(f)
    if(newTags.isEmpty) None
    else Some(IfAnyOf(newTags))
  }
}
case class AndPrecondition(a: Precondition, b: Precondition) extends Precondition {
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean) =
    a.passes(tag, sideEffectFree) match {
      case f: Precondition.Failure => f
      case Precondition.Passed => b.passes(tag, sideEffectFree)
    }

  def map(f: (EntityTag) => EntityTag): Precondition =
    AndPrecondition(a.map(f), b.map(f))

  def filter(f: EntityTag => Boolean): Option[Precondition] =
    for {
      aPrime <- a.filter(f)
      bPrime <- b.filter(f)
    } yield AndPrecondition(aPrime, bPrime)
}

object Precondition {
  sealed abstract class Result
  case object Passed extends Result
  sealed abstract class Failure extends Result
  case object FailedBecauseMatch extends Failure
  case object FailedBecauseNoMatch extends Failure

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
