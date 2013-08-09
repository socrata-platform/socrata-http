package com.socrata.http.server.`routing-impl`.two

import com.socrata.http.server.routing.two.{Extractor, PathTree}
import scala.runtime.AbstractFunction1

object Extract {
  def apply[T : Extractor](p: PathTree[String, List[Any]]): String => Option[PathTree[String, List[Any]]] =
    new AbstractFunction1[String, Option[PathTree[String, List[Any]]]] {
      private[this] val extractor = Extractor[T]
      def apply(s: String) = extractor.extract(s).map { r => p.map(r :: _) }
    }
}
