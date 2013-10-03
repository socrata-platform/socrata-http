package com.socrata.http.server.util

import com.socrata.http.server.implicits._
import javax.servlet.http.HttpServletRequest

object PreconditionParser {
  private def ifMatchPrecondition(req: HttpServletRequest): Option[Precondition] =
    req.header("If-Match") map { s =>
      if(s == "*") IfExists
      else IfAnyOf(EntityTagParser.parseList(s))
    }

  def precondition(req: HttpServletRequest): Precondition =
    req.header("If-None-Match") match {
      case Some(s) =>
        val inmPrecondition =
          if(s == "*") IfDoesNotExist
          else IfNoneOf(EntityTagParser.parseList(s))

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
