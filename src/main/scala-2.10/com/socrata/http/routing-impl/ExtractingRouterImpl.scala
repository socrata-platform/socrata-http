package com.socrata.http.`routing-impl`

import scala.language.experimental.macros
import scala.reflect.macros.Context

import com.socrata.http.routing.Router

object ExtractingRouterImpl {
  def impl[U: c.WeakTypeTag](c: Context)(method: c.Expr[String], route: c.Expr[String])(targetObject: c.Expr[Any]): c.Expr[Router[U]] = {
    import c.universe._

    val path = route.tree match {
      case Literal(Constant(s: String)) => s
      case other => c.abort(other.pos, "The `route' arrgument to a SmartRoute must be a string literal")
    }

    if(!path.startsWith("/")) {
      c.abort(route.tree.pos, "The `route' argument must start with /")
    }

    val pathElementsUntrimmed = path.split('/').drop(1).toList
    val hasStar = pathElementsUntrimmed.tails.find(_.headOption == Some("*")) match {
      case Some("*" :: Nil) => true
      case Some(_) => c.abort(route.tree.pos, "Any * component must come last")
      case _ => false
    }

    val pathElements = pathElementsUntrimmed.dropRight(if(hasStar) 1 else 0)

    def TermName(s: String) = newTermName(s: String)
    def TypeName(s: String) = newTypeName(s: String)

    val incomingMethod, incomingPath, targetHolder, methodHolder, i = TermName(c.fresh())

    def loop(remainingRoutes: List[String], vars: List[TermName]): Tree = remainingRoutes match {
      case Nil =>
        val vs = vars.reverse
        val t = if(hasStar) {
          Apply(Select(Ident(targetHolder), newTermName("apply")), vs.map(Ident(_)) :+ Select(Ident(i), TermName("toList")))
        } else {
          If(Select(Ident(i), TermName("hasNext")),
            Return(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("None"))),
            Apply(Select(Ident(targetHolder), newTermName("apply")), vs.map(Ident(_))))
        }
        // q"return _root_.scala.Some($t)"
        Return(Apply(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("Some")), List(t)))
      case "?" :: tl =>
        val v = newTermName(c.fresh())
        val inner = loop(tl, v :: vars)
        /*
                q"""
        if($i.hasNext) {
          val $v = $i.next()
          $inner
        }"""
        */
        If(Select(Ident(i), TermName("hasNext")),
          Block(List(
            ValDef(Modifiers(), v, TypeTree(), Apply(Select(Ident(i), TermName("next")), List()))),
            inner),
          Literal(Constant(())))
      case lit :: tl =>
        val inner = loop(tl, vars)
        // q"""if($i.hasNext && $i.next() == $lit) { $inner }"""
        If(Apply(Select(Select(Ident(i), TermName("hasNext")), TermName("$amp$amp")), List(Apply(Select(Apply(Select(Ident(i), TermName("next")), List()), TermName("$eq$eq")), List(Literal(Constant(lit)))))),
          Block(List(), inner),
          Literal(Constant(())))
    }
    val body = loop(pathElements, Nil)
    /*
        val res = q"""
    new __root__.scala.Function2[__root__.scala.Predef.String, __root__.scala.Seq[__root__.scala.Predef.String], __root__.scala.Option[${TypeTree(weakTypeOf[U])}]] {
      private[this] val $methodHolder = ${method.tree}
      private[this] val $targetHolder = ${targetObject.tree}
      def apply($incomingMethod : __root__.scala.String, $incomingPath : __root__.scala.Seq[__root__.scala.Predef.String]): __root__.scala.Option[${TypeTree(weakTypeOf[U])}] = {
        if($incomingMethod == $methodHolder) {
          val $i = $incomingPath.iterator
          $body
        }
        _root_.scala.None
      }
    }"""
    */
    val pendingSuperCall = Apply(Select(Super(This(tpnme.EMPTY), tpnme.EMPTY), nme.CONSTRUCTOR), List())
    val res =
      Block(List(
        ClassDef(Modifiers(Flag.FINAL), TypeName("$anon"), List(), Template(List(AppliedTypeTree(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TypeName("Function2")), List(Select(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("Predef")), TypeName("String")), AppliedTypeTree(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TypeName("Seq")), List(Select(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("Predef")), TypeName("String")))), AppliedTypeTree(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TypeName("Option")), List(TypeTree(weakTypeOf[U])))))),
          emptyValDef,
          List(DefDef(Modifiers(), nme.CONSTRUCTOR, List(), List(List()), TypeTree(), Block(List(pendingSuperCall), Literal(Constant(())))),
            ValDef(Modifiers(Flag.PRIVATE | Flag.LOCAL), methodHolder, TypeTree(), method.tree),
            ValDef(Modifiers(Flag.PRIVATE | Flag.LOCAL), targetHolder, TypeTree(), targetObject.tree),
            DefDef(Modifiers(), TermName("apply"), List(), List(List(ValDef(Modifiers(Flag.PARAM), incomingMethod, Select(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("Predef")), TypeName("String")), EmptyTree), ValDef(Modifiers(Flag.PARAM), incomingPath, AppliedTypeTree(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TypeName("Seq")), List(Select(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("Predef")), TypeName("String")))), EmptyTree))), AppliedTypeTree(Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TypeName("Option")), List(TypeTree(weakTypeOf[U]))),
              Block(List(If(Apply(Select(Ident(incomingMethod), TermName("$eq$eq")), List(Ident(methodHolder))),
                Block(List(ValDef(Modifiers(), i, TypeTree(), Select(Ident(incomingPath), TermName("iterator")))),
                  body),
                Literal(Constant(())))),
                Select(Select(Ident(nme.ROOTPKG), TermName("scala")), TermName("None")))))))),
        Apply(Select(New(Ident(TypeName("$anon"))), nme.CONSTRUCTOR), List()))

    c.Expr[Router[U]](res)
  }
}
