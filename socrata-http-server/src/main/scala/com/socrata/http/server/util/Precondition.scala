package com.socrata.http.server.util

import com.socrata.http.common.util.{HttpUtils, HeaderParser, HttpHeaderParseException}
import scala.annotation.tailrec
import javax.servlet.http.HttpServletRequest
import com.socrata.http.server.implicits._
import org.apache.commons.codec.binary.Base64
import com.socrata.http.server.util.Precondition.{Result, FailedBecauseNoMatch}

// TODO: Although intended to implement HTTP If-[None-]Match handling, none of this is
// actually HTTP-specific.  This should be moved into a non-HTTP package.

sealed abstract class EntityTag(value: Array[Byte]) {
  val asBytesUnsafe = value.clone()
  def asBytes = asBytesUnsafe.clone()

  def weakCompare(that: EntityTag) = java.util.Arrays.equals(this.asBytesUnsafe, that.asBytesUnsafe)
  def strongCompare(that: EntityTag): Boolean

  def map(f: Array[Byte] => Array[Byte]): EntityTag

  override final def toString = getClass.getSimpleName + "(\"" + Base64.encodeBase64URLSafeString(asBytesUnsafe) + "\")"
}

final class WeakEntityTag(value: Array[Byte]) extends EntityTag(value) {
  def strongCompare(that: EntityTag): Boolean = false
  def map(f: Array[Byte] => Array[Byte]) = WeakEntityTag(f(asBytes))
}

object WeakEntityTag extends (Array[Byte] => WeakEntityTag) {
  def apply(value: Array[Byte]) = new WeakEntityTag(value)
}

final class StrongEntityTag(value: Array[Byte]) extends EntityTag(value) {
  def strongCompare(that: EntityTag): Boolean = that match {
    case s: StrongEntityTag => java.util.Arrays.equals(asBytesUnsafe, s.asBytesUnsafe)
    case _: WeakEntityTag => false
  }
  def map(f: Array[Byte] => Array[Byte]) = StrongEntityTag(f(value))
}

object StrongEntityTag extends (Array[Byte] => StrongEntityTag) {
  def apply(value: Array[Byte]) = new StrongEntityTag(value)
  override def toString = "StrongEntityTag"
}

sealed abstract class Precondition {
  @deprecated("Use `check' instead", since = "2.0.0")
  def passes(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result = check(tag, sideEffectFree)
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result

  // Eliminates some tags from consideration.  This exists so services can nest
  // etags from inner services within etags of their own.
  //
  // I would like this to always return a Precondition, but it's possible that
  // the predicate will eliminate all the etags, and on an If-Match, the resulting
  // precondition would be inexpressible in HTTP.
  def filter(f: EntityTag => Boolean): Either[Precondition.FailedBecauseNoMatch.type, Precondition]
  def map(f: EntityTag => EntityTag): Precondition
}
case object NoPrecondition extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean) = Precondition.Passed
  def filter(f: EntityTag => Boolean) = Right(this)
  def map(f: EntityTag => EntityTag) = this
}
case object IfDoesNotExist extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean) = if(tag.nonEmpty) Precondition.FailedBecauseMatch(List(tag.get)) else Precondition.Passed
  def filter(f: EntityTag => Boolean) = Right(this)
  def map(f: EntityTag => EntityTag) = this
}
case class IfNoneOf(etag: Seq[EntityTag]) extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean) = tag match {
    case Some(t) =>
      val matches =
        if(sideEffectFree) etag.find(_.weakCompare(t))
        else etag.find(_.strongCompare(t))
      matches match {
        case Some(matched) => Precondition.FailedBecauseMatch(List(matched))
        case None => Precondition.Passed
      }
    case None =>
      Precondition.Passed
  }
  def filter(f: EntityTag => Boolean) = {
    val newTags = etag.filter(f)
    if(newTags.isEmpty) Right(NoPrecondition)
    else Right(IfNoneOf(newTags))
  }
  def map(f: EntityTag => EntityTag) = IfNoneOf(etag.map(f))
}
case object IfExists extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean) = if(tag.isEmpty) Precondition.FailedBecauseNoMatch else Precondition.Passed
  def filter(f: EntityTag => Boolean) = Right(this)
  def map(f: EntityTag => EntityTag) = this
}
case class IfAnyOf(etag: Seq[EntityTag]) extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean) = tag match {
    case Some(t) =>
      if(etag.exists(_.strongCompare(t))) Precondition.Passed
      else Precondition.FailedBecauseNoMatch
    case None =>
      Precondition.Passed
  }
  def map(f: EntityTag => EntityTag) = IfAnyOf(etag.map(f))
  def filter(f: EntityTag => Boolean) = {
    val newTags = etag.filter(f)
    if(newTags.isEmpty) Left(Precondition.FailedBecauseNoMatch)
    else Right(IfAnyOf(newTags))
  }
}
case class AndPrecondition(a: Precondition, b: Precondition) extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean) =
    a.check(tag, sideEffectFree) match {
      case f: Precondition.Failure => f
      case Precondition.Passed => b.check(tag, sideEffectFree)
    }

  def map(f: (EntityTag) => EntityTag): Precondition =
    AndPrecondition(a.map(f), b.map(f))

  def filter(f: EntityTag => Boolean) =
    for {
      aPrime <- a.filter(f).right
      bPrime <- b.filter(f).right
    } yield AndPrecondition(aPrime, bPrime)
}

object Precondition {
  sealed abstract class Result
  case object Passed extends Result
  sealed abstract class Failure extends Result
  case class FailedBecauseMatch(etag: Seq[EntityTag]) extends Failure
  case object FailedBecauseNoMatch extends Failure
}
