package com.socrata.http.server.`routing-impl`

import scala.language.experimental.macros
import scala.reflect.macros.Context
import com.socrata.http.server.routing.PathTree
import com.socrata.http.server.HttpService
import javax.servlet.http.HttpServletRequest

object RouteImpl {
  private val priorityProvider = new java.util.concurrent.atomic.AtomicLong(0L)
  def nextPriority() = priorityProvider.getAndIncrement()

  def impl(c: Context)(pathSpec: c.Expr[String], targetObject: c.Expr[Any]): c.Expr[PathTree[String, HttpService]] = {
    import c.universe._
    val tree = q"_root_.com.socrata.http.server.routing.PathTreeBuilder[_root_.com.socrata.http.server.HttpService](_root_.com.socrata.http.server.`routing-impl`.RouteImpl.nextPriority(), $pathSpec)($targetObject)"
    c.Expr[PathTree[String, HttpService]](tree)
  }
}

object DirectoryImpl {
  def impl(c: Context)(pathSpec: c.Expr[String]): c.Expr[PathTree[String, HttpService]] = {
    import c.universe._

    val (pathComponents, hasStar) = PathTreeBuilderImpl.parsePathInfo(c)(pathSpec)

    if(hasStar) c.error(pathSpec.tree.pos, "A directory cannot end with a flexmatch")

    def typeFromName(s: String) = {
      val TypeDef(_, _, _, rhs) = c.parse("type T = " + s)
      rhs
    }

    val fTree = locally {
      val argTypes = pathComponents.collect { case PathTreeBuilderImpl.PathInstance(_) =>
        tq"_root_.scala.Any"
      } ++ (if(hasStar) Seq(tq"_root_.scala.Any") else Nil)
      val typeName = typeFromName(s"_root_.scala.runtime.AbstractFunction" + argTypes.length)
      val typeParams = argTypes :+ tq"_root_.com.socrata.http.server.HttpService"

      // doing this via string building because building it the "right" way (building
      // the args as ValDefs and splicing them in) makes user classes require the macro-paradise
      // plugin to compile.
      val args = argTypes.zipWithIndex.map { case (_, i) => "x$i : _root_.scala.Any" }.mkString(",")
      val defdef = c.parse(s"def apply($args) = _root_.com.socrata.http.server.`routing-impl`.DirectoryImpl.redirect")
      q"new $typeName[..$typeParams] { $defdef }"
    }

    val tree = q"_root_.com.socrata.http.server.routing.PathTreeBuilder[_root_.com.socrata.http.server.HttpService](_root_.com.socrata.http.server.`routing-impl`.RouteImpl.nextPriority(), $pathSpec)($fTree)"

    c.Expr[PathTree[String, HttpService]](tree)
  }

  val redirect = { (req: HttpServletRequest) =>
    val sb = req.getRequestURL.append('/')
    if(req.getQueryString != null) sb.append('?').append(req.getQueryString)
    com.socrata.http.server.responses.MovedPermanently(new java.net.URL(sb.toString))
  }
}
