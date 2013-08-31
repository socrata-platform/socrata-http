package com.socrata.http.server.`routing-impl`

import com.socrata.http.server.routing._
import scala.runtime.AbstractFunction1
import com.socrata.http.server.routing.TypedPathComponent
import scala.Some
import com.socrata.http.server.routing.OptionallyTypedPathComponent

object Extract {
  class TypedPathComponentExtractor[T](val extMatcher: String => Boolean)(implicit val extractor: Extractor[T]) extends Extractor[TypedPathComponent[T]] {
    def extract(s: String): Option[TypedPathComponent[T]] = {
      val lastDot = s.lastIndexOf('.')
      if(lastDot == -1) None
      else s.substring(lastDot + 1) match {
        case r if extMatcher(r) =>
          extractor.extract(s.substring(0, lastDot)).map(TypedPathComponent(_, r))
        case _ =>
          None
      }
    }

    override def equals(o: Any) = o match {
      case that: TypedPathComponentExtractor[_] => this.extMatcher == that.extMatcher && this.extractor == that.extractor
      case _ => false
    }

    override def hashCode = extMatcher.hashCode ^ extractor.hashCode ^ -1351727963
  }

  def typedExtractor[T : Extractor](extMatcher: String => Boolean) : Extractor[TypedPathComponent[T]] =
    new TypedPathComponentExtractor[T](extMatcher)

  class OptionallyTypedPathComponentExtractor[T](extMatcher: String => Boolean)(implicit extractor: Extractor[T]) extends Extractor[OptionallyTypedPathComponent[T]] {
    def extract(s: String): Option[OptionallyTypedPathComponent[T]] = {
      val lastDot = s.lastIndexOf('.')
      if(lastDot == -1) extractor.extract(s).map(OptionallyTypedPathComponent(_, None))
      else s.substring(lastDot + 1) match {
        case r if extMatcher(r) =>
          extractor.extract(s.substring(0, lastDot)).map(OptionallyTypedPathComponent(_, Some(r)))
        case _ =>
          None
      }
    }

    override def equals(o: Any) = o match {
      case that: TypedPathComponentExtractor[_] => this.extMatcher == that.extMatcher && this.extractor == that.extractor
      case _ => false
    }

    override def hashCode = extMatcher.hashCode ^ extractor.hashCode ^ 581869302
  }

  def optionallyTypedExtractor[T](extMatcher: String => Boolean)(implicit extractor: Extractor[T]) : Extractor[OptionallyTypedPathComponent[T]] =
    new OptionallyTypedPathComponentExtractor[T](extMatcher)
}
