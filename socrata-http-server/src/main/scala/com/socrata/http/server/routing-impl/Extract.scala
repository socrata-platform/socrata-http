package com.socrata.http.server.`routing-impl`

import com.socrata.http.server.routing.{OptionallyTypedPathComponent, TypedPathComponent, Extractor, PathTree}
import scala.runtime.AbstractFunction1
import scala.util.matching.Regex

object Extract {
  def apply[T : Extractor](p: PathTree[String, List[Any]]): String => Option[PathTree[String, List[Any]]] =
    explicit(Extractor[T], p)

  def explicit[T](extractor: Extractor[T], p: PathTree[String, List[Any]]): String => Option[PathTree[String, List[Any]]] =
    new AbstractFunction1[String, Option[PathTree[String, List[Any]]]] {
      def apply(s: String) = extractor.extract(s).map { r => p.map(r :: _) }
    }

  def typedExtractor[T](extRegex: Regex)(implicit extractor: Extractor[T]) : Extractor[TypedPathComponent[T]] = new Extractor[TypedPathComponent[T]] {
    def extract(s: String): Option[TypedPathComponent[T]] = {
      val lastDot = s.lastIndexOf('.')
      if(lastDot == -1) None
      else s.substring(lastDot + 1) match {
        case r if extRegex.pattern.matcher(r).matches =>
          extractor.extract(s.substring(0, lastDot)).map(TypedPathComponent(_, r))
        case _ =>
          None
      }
    }
  }

  def optionallyTypedExtractor[T](extRegex: Regex)(implicit extractor: Extractor[T]) : Extractor[OptionallyTypedPathComponent[T]] = new Extractor[OptionallyTypedPathComponent[T]] {
    def extract(s: String): Option[OptionallyTypedPathComponent[T]] = {
      val lastDot = s.lastIndexOf('.')
      if(lastDot == -1) extractor.extract(s).map(OptionallyTypedPathComponent(_, None))
      else s.substring(lastDot + 1) match {
        case r if extRegex.pattern.matcher(r).matches =>
          extractor.extract(s.substring(0, lastDot)).map(OptionallyTypedPathComponent(_, Some(r)))
        case _ =>
          None
      }
    }
  }
}
