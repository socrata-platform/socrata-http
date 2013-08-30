package com.socrata.http.server.routing

import scala.language.experimental.macros
import com.socrata.http.server.`routing-impl`.PathTreeBuilderImpl

object PathTreeBuilder {
  def apply[U](pathSpec: String)(targetObject: Any) = macro PathTreeBuilderImpl.impl[U]
}
