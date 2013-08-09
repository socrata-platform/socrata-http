package com.socrata.http.server.routing.two

import scala.language.experimental.macros
import com.socrata.http.server.`routing-impl`.two.PathTreeBuilderImpl

object PathTreeBuilder {
  def apply[U](priority: Int, pathSpec: String)(targetObject: Any) = macro PathTreeBuilderImpl.impl[U]
}
