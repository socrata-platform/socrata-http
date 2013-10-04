package com.socrata.http.server.util

object PreconditionRenderer extends (Precondition => Seq[(String, String)]) {
  def apply(precondition: Precondition): Seq[(String, String)] = precondition match {
    case IfDoesNotExist => List("If-None-Match" -> "*")
    case IfExists => List("If-Match" -> "*")
    case IfAnyOf(etags) => List("If-Match" -> etags.map(EntityTagRenderer).mkString(","))
    case IfNoneOf(etags) => List("If-None-Match" -> etags.map(EntityTagRenderer).mkString(","))
    case AndPrecondition(a, b) => apply(a) ++ apply(b)
    case NoPrecondition => Nil
  }
}
