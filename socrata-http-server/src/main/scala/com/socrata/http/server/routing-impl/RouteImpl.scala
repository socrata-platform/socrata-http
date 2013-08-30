package com.socrata.http.server.`routing-impl`

import scala.language.experimental.macros
import scala.reflect.macros.Context
import com.socrata.http.server.routing.PathTree2
import javax.servlet.http.HttpServletRequest
import com.socrata.http.server.routing.IsHttpService

object RouteImpl {
  def route[From : c.WeakTypeTag, To : c.WeakTypeTag](c: Context)(pathSpec: c.Expr[String], targetObject: c.Expr[Any]): c.Expr[PathTree2[List[String] => From => To]] = {
    import c.universe._

    val fromTree = TypeTree(weakTypeOf[From])
    val toTree = TypeTree(weakTypeOf[To])

    val tree = q"_root_.com.socrata.http.server.routing.PathTreeBuilder[$fromTree => $toTree]($pathSpec)($targetObject)"
    c.Expr[PathTree2[List[String] => From => To]](tree)
  }

  def dir[From : c.WeakTypeTag, To: c.WeakTypeTag](c: Context)(pathSpec: c.Expr[String])(ihs: c.Expr[IsHttpService[From => To]]): c.Expr[PathTree2[List[String] => From => To]] = {
    import c.universe._

    val (pathComponents, hasStar) = PathTreeBuilderImpl.parsePathInfo(c)(pathSpec)

    if(hasStar) c.error(pathSpec.tree.pos, "A directory cannot end with a flexmatch")

    def typeFromName(s: String) = {
      val TypeDef(_, _, _, rhs) = c.parse("type T = " + s)
      rhs
    }

    val fromTree = TypeTree(weakTypeOf[From])
    val toTree = TypeTree(weakTypeOf[To])
    val ihsName = newTermName(c.fresh("ihs"))
    val wrappedRedirectName = newTermName(c.fresh("redirect"))
    val wrappedRedirect = q"$ihsName.wrap(_root_.com.socrata.http.server.`routing-impl`.RouteImpl.redirect)"

    val fTree = locally {
      val argTypes = pathComponents.collect { case PathTreeBuilderImpl.PathInstance(_) =>
        tq"_root_.scala.Any"
      }

      if(argTypes.nonEmpty) {
        val typeName = typeFromName(s"_root_.scala.runtime.AbstractFunction" + argTypes.length)
        val typeParams = argTypes :+ tq"$fromTree => $toTree"

        // doing this via string building because building it the "right" way (building
        // the args as ValDefs and splicing them in) makes user classes require the macro-paradise
        // plugin to compile.
        val args = argTypes.zipWithIndex.map { case (_, i) => s"x$i : _root_.scala.Any" }.mkString(",")
        val defdef = c.parse(s"def apply($args) = $wrappedRedirectName")
        q"new $typeName[..$typeParams] { $defdef }"
      } else {
        q"$wrappedRedirectName"
      }
    }

    val tree = q"""{
  val $ihsName = $ihs
  val $wrappedRedirectName = $wrappedRedirect
  _root_.com.socrata.http.server.routing.PathTreeBuilder[$fromTree => $toTree]($pathSpec)($fTree)
}"""

    c.Expr[PathTree2[List[String] => From => To]](tree)
  }

  val redirect = { (req: HttpServletRequest) =>
    val sb = req.getRequestURL.append('/')
    if(req.getQueryString != null) sb.append('?').append(req.getQueryString)
    com.socrata.http.server.responses.MovedPermanently(new java.net.URL(sb.toString))
  }
}
