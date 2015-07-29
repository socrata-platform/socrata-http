package com.socrata.http.server.util

// Note: it is subclasses' responsibility to copy the input
// TODO: Although intended to implement HTTP If-[None-]Match handling, none of this is
// actually HTTP-specific.  This should be moved into a non-HTTP package.
sealed abstract class EntityTag (val asBytesUnsafe: Array[Byte]) {
  def asBytes: Array[Byte] = asBytesUnsafe.clone()

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

  def weakCompare(that: EntityTag): Boolean = java.util.Arrays.equals(this.asBytesUnsafe, that.asBytesUnsafe)
  def strongCompare(that: EntityTag): Boolean

  def map(f: Array[Byte] => Array[Byte]): EntityTag
  // special cases of map
  def append(bytes: Array[Byte]): EntityTag
  def prepend(bytes: Array[Byte]): EntityTag
  def drop(n: Int): EntityTag
  def dropRight(n: Int): EntityTag

  override final def toString: String = getClass.getSimpleName + "(" + asBytesUnsafe.mkString(",") + ")"
}

object EntityTag {
  def appendBytes(a: Array[Byte], b: Array[Byte]): Array[Byte] = {
    val res = new Array[Byte](a.length + b.length)
    System.arraycopy(a, 0, res, 0, a.length)
    System.arraycopy(b, 0, res, a.length, b.length)
    res
  }

  def dropBytes(a: Array[Byte], n: Int): Array[Byte] = {
    if (n >= a.length) {
      new Array[Byte](0)
    } else if(n <= 0) {
      a
    } else {
      val res = new Array[Byte](a.length - n)
      System.arraycopy(a, n, res, 0, res.length)
      res
    }
  }

  def dropRightBytes(a: Array[Byte], n: Int): Array[Byte] = {
    if (n >= a.length) {
      new Array[Byte](0)
    } else if(n <= 0) {
      a
    } else {
      val res = new Array[Byte](a.length - n)
      System.arraycopy(a, 0, res, 0, res.length)
      res
    }
  }
}

final class WeakEntityTag private (value: Array[Byte], dummy: Boolean) extends EntityTag(value) {
  def this(value: Array[Byte]) = this(value.clone(), false)

  def strongCompare(that: EntityTag): Boolean = false
  def map(f: Array[Byte] => Array[Byte]): WeakEntityTag = WeakEntityTag(f(asBytes))
  def append(bytes: Array[Byte]): WeakEntityTag = new WeakEntityTag(EntityTag.appendBytes(asBytesUnsafe, bytes), false)
  def prepend(bytes: Array[Byte]): WeakEntityTag = new WeakEntityTag(EntityTag.appendBytes(bytes, asBytesUnsafe), false)
  def drop(n: Int): WeakEntityTag = new WeakEntityTag(EntityTag.dropBytes(asBytesUnsafe, n), false)
  def dropRight(n: Int): WeakEntityTag = new WeakEntityTag(EntityTag.dropRightBytes(asBytesUnsafe, n), false)
}

object WeakEntityTag extends (Array[Byte] => WeakEntityTag) {
  def apply(value: Array[Byte]): WeakEntityTag = new WeakEntityTag(value)
}

final class StrongEntityTag private (value: Array[Byte], dummy: Boolean) extends EntityTag(value) {
  def this(value: Array[Byte]) = this(value.clone(), false)

  def strongCompare(that: EntityTag): Boolean = that match {
    case s: StrongEntityTag => java.util.Arrays.equals(asBytesUnsafe, s.asBytesUnsafe)
    case _: WeakEntityTag => false
  }
  def map(f: Array[Byte] => Array[Byte]): StrongEntityTag = StrongEntityTag(f(asBytes))
  def append(bytes: Array[Byte]): StrongEntityTag =
    new StrongEntityTag(EntityTag.appendBytes(asBytesUnsafe, bytes), false)
  def prepend(bytes: Array[Byte]): StrongEntityTag =
    new StrongEntityTag(EntityTag.appendBytes(bytes, asBytesUnsafe), false)
  def drop(n: Int): StrongEntityTag = new StrongEntityTag(EntityTag.dropBytes(asBytesUnsafe, n), false)
  def dropRight(n: Int): StrongEntityTag = new StrongEntityTag(EntityTag.dropRightBytes(asBytesUnsafe, n), false)
}

object StrongEntityTag extends (Array[Byte] => StrongEntityTag) {
  def apply(value: Array[Byte]): StrongEntityTag = new StrongEntityTag(value)
  override def toString(): String = "StrongEntityTag"
}

sealed abstract class Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result

  // Eliminates some tags from consideration.  This exists so services can nest
  // etags from inner services within etags of their own.
  //
  // I would like this to always return a Precondition, but it's possible that
  // the predicate will eliminate all the etags, and on an If-Match, the resulting
  // precondition would be inexpressible in HTTP.
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition]
  def map(f: EntityTag => EntityTag): Precondition
}
case object NoPrecondition extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result = Precondition.Passed
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition] = Right(this)
  def map(f: EntityTag => EntityTag): Precondition = this
}
case object IfDoesNotExist extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result =
    if (tag.nonEmpty) Precondition.FailedBecauseMatch(List(tag.get)) else Precondition.Passed
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition] = Right(this)
  def map(f: EntityTag => EntityTag): Precondition = this
}
case class IfNoneOf(etag: Seq[EntityTag]) extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result = tag match {
    case None => Precondition.Passed
    case Some(t) =>
      (if (sideEffectFree) etag.find(_.weakCompare(t)) else etag.find(_.strongCompare(t))) match {
        case None => Precondition.Passed
        case Some(matched) => Precondition.FailedBecauseMatch(List(matched))
      }
  }
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition] = {
    val newTags = etag.filter(f)
    if (newTags.isEmpty) Right(NoPrecondition) else Right(IfNoneOf(newTags))
  }
  def map(f: EntityTag => EntityTag): Precondition = IfNoneOf(etag.map(f))
}
case object IfExists extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result =
    if (tag.isEmpty) Precondition.FailedBecauseNoMatch else Precondition.Passed
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition] = Right(this)
  def map(f: EntityTag => EntityTag): Precondition = this
}
case class IfAnyOf(etag: Seq[EntityTag]) extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result = tag match {
    case None => Precondition.Passed
    case Some(t) => if (etag.exists(_.strongCompare(t))) Precondition.Passed else Precondition.FailedBecauseNoMatch
  }
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition] = {
    val newTags = etag.filter(f)
    if (newTags.isEmpty) Left(Precondition.FailedBecauseNoMatch) else Right(IfAnyOf(newTags))
  }
  def map(f: EntityTag => EntityTag): Precondition = IfAnyOf(etag.map(f))
}
case class AndPrecondition(a: Precondition, b: Precondition) extends Precondition {
  def check(tag: Option[EntityTag], sideEffectFree: Boolean): Precondition.Result =
    a.check(tag, sideEffectFree) match {
      case f: Precondition.Failure => f
      case Precondition.Passed => b.check(tag, sideEffectFree)
    }
  def filter(f: EntityTag => Boolean): Either[Precondition.Failure, Precondition] =
    for {
      aPrime <- a.filter(f).right
      bPrime <- b.filter(f).right
    } yield AndPrecondition(aPrime, bPrime)

  def map(f: (EntityTag) => EntityTag): Precondition =
    AndPrecondition(a.map(f), b.map(f))

}

object Precondition {
  sealed abstract class Result
  case object Passed extends Result
  sealed abstract class Failure extends Result
  case class FailedBecauseMatch(etag: Seq[EntityTag]) extends Failure
  case object FailedBecauseNoMatch extends Failure
}
