package com.socrata.http.server.util

// TODO: Although intended to implement HTTP If-[None-]Match handling, none of this is
// actually HTTP-specific.  This should be moved into a non-HTTP package.

sealed abstract class EntityTag (val asBytesUnsafe: Array[Byte]) { // note: it is subclasses' responsibility to copy the input
  def asBytes = asBytesUnsafe.clone()

  def startsWith(bytes: Array[Byte]): Boolean =
    bytes.length <= asBytesUnsafe.length && {
      var i = bytes.length - 1
      while(i >= 0 && asBytesUnsafe(i) == bytes(i)) i -= 1
      i == -1
    }

  def endsWith(bytes: Array[Byte]): Boolean =
    bytes.length <= asBytesUnsafe.length && {
      var i = asBytesUnsafe.length - 1
      val d = asBytesUnsafe.length - bytes.length
      val end = d - 1
      while(i != end && asBytesUnsafe(i) == bytes(i - d)) i -= 1
      i == end
    }

  def weakCompare(that: EntityTag) = java.util.Arrays.equals(this.asBytesUnsafe, that.asBytesUnsafe)
  def strongCompare(that: EntityTag): Boolean

  def map(f: Array[Byte] => Array[Byte]): EntityTag
  // special cases of map
  def append(bytes: Array[Byte]): EntityTag
  def prepend(bytes: Array[Byte]): EntityTag
  def drop(n: Int): EntityTag
  def dropRight(n: Int): EntityTag

  override final def toString = getClass.getSimpleName + "(" + asBytesUnsafe.mkString(",") + ")"
}

object EntityTag {
  def appendBytes(a: Array[Byte], b: Array[Byte]) = {
    val res = new Array[Byte](a.length + b.length)
    System.arraycopy(a, 0, res, 0, a.length)
    System.arraycopy(b, 0, res, a.length, b.length)
    res
  }

  def dropBytes(a: Array[Byte], n: Int): Array[Byte] = {
    if(n >= a.length) new Array[Byte](0)
    else if(n <= 0) a
    else {
      val res = new Array[Byte](a.length - n)
      System.arraycopy(a, n, res, 0, res.length)
      res
    }
  }

  def dropRightBytes(a: Array[Byte], n: Int): Array[Byte] = {
    if(n >= a.length) new Array[Byte](0)
    else if(n <= 0) a
    else {
      val res = new Array[Byte](a.length - n)
      System.arraycopy(a, 0, res, 0, res.length)
      res
    }
  }
}

final class WeakEntityTag private (value: Array[Byte], dummy: Boolean) extends EntityTag(value) {
  def this(value: Array[Byte]) = this(value.clone(), false)

  def strongCompare(that: EntityTag): Boolean = false
  def map(f: Array[Byte] => Array[Byte]) = WeakEntityTag(f(asBytes))
  def append(bytes: Array[Byte]) = new WeakEntityTag(EntityTag.appendBytes(asBytesUnsafe, bytes), false)
  def prepend(bytes: Array[Byte]) = new WeakEntityTag(EntityTag.appendBytes(bytes, asBytesUnsafe), false)
  def drop(n: Int) = new WeakEntityTag(EntityTag.dropBytes(asBytesUnsafe, n), false)
  def dropRight(n: Int) = new WeakEntityTag(EntityTag.dropRightBytes(asBytesUnsafe, n), false)
}

object WeakEntityTag extends (Array[Byte] => WeakEntityTag) {
  def apply(value: Array[Byte]) = new WeakEntityTag(value)
}

final class StrongEntityTag private (value: Array[Byte], dummy: Boolean) extends EntityTag(value) {
  def this(value: Array[Byte]) = this(value.clone(), false)

  def strongCompare(that: EntityTag): Boolean = that match {
    case s: StrongEntityTag => java.util.Arrays.equals(asBytesUnsafe, s.asBytesUnsafe)
    case _: WeakEntityTag => false
  }
  def map(f: Array[Byte] => Array[Byte]) = StrongEntityTag(f(asBytes))
  def append(bytes: Array[Byte]) = new StrongEntityTag(EntityTag.appendBytes(asBytesUnsafe, bytes), false)
  def prepend(bytes: Array[Byte]) = new StrongEntityTag(EntityTag.appendBytes(bytes, asBytesUnsafe), false)
  def drop(n: Int) = new StrongEntityTag(EntityTag.dropBytes(asBytesUnsafe, n), false)
  def dropRight(n: Int) = new StrongEntityTag(EntityTag.dropRightBytes(asBytesUnsafe, n), false)
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
