package com.socrata.http.server.`routing-impl`

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.util.parsing.combinator.RegexParsers
import com.socrata.http.server.routing.PathTree

object PathTreeBuilderImpl {
  sealed abstract class ComponentType
  case class PathInstance(className: String) extends ComponentType
  case class PathTypedInstance(className: String, regexIdentifier: String, extRequired: Boolean) extends ComponentType
  case class PathLiteral(value: String) extends ComponentType

  val standardRegexName = "_root_.com.socrata.http.server.`routing-impl`.PathTreeBuilderAux.StandardRegex"

  class Parser extends RegexParsers {
    val identifier = "[a-zA-Z0-9_]+".r
    val dot = "."
    // "(?=/|$)" == "lookahead for slash or end of input"
    val qualName = rep1sep(identifier, dot) ^^ { xs => xs.mkString(".") }
    val classNamePattern = ("{" ~>  qualName <~ "}(?=/|$)".r) ||| ("\\?(?=/|$)".r ^^^ "_root_.scala.Predef.String")
    val classNameWithExtPattern = "{{" ~> qualName ~ opt("[:!]".r ~ opt(qualName)) <~ "}}(?=/|$)".r ^^ {
      case cn ~ Some(":" ~ rn) => (cn, rn.getOrElse(standardRegexName), false)
      case cn ~ Some("!" ~ rn) => (cn, rn.getOrElse(standardRegexName), true)
      case cn ~ None => (cn, standardRegexName, false)
    }
    // "any single character that is not { or / or * or ?, or a character that is not { or / followed by one or more characters that are not /, or no characters, all followed by end of component"
    val lit = "(?:[^{/*?]|[^{/][^/]+|)(?=/|$)".r
    val slash = "/"
    val star = "\\*(?=/|$)".r
    val dir = slash ~> ((lit ^^ PathLiteral) ||| (classNamePattern ^^ PathInstance) ||| (classNameWithExtPattern ^^ PathTypedInstance.tupled))
    val path1 = rep1(dir) ~ opt(slash ~ star) ^^ { case a ~ b => (a, b.isDefined) }
    val path0 = slash ~ star ^^ { case a ~ b => (Nil, true) }
    val path = path0 ||| path1
  }

  def parsePathInfo(c: Context)(pathSpec: c.Expr[String]) : (Seq[ComponentType], Boolean) = {
    import c.universe._

    val path = pathSpec.tree match {
      case Literal(Constant(s: String)) => s
      case other => c.abort(other.pos, "The `pathSpec' argument must be a string literal")
    }

    val parser = new Parser
    val (pathElements, hasStar) = parser.parseAll(parser.path, path) match {
      case parser.Success(elems, _) => elems
      case fail: parser.NoSuccess => c.abort(pathSpec.tree.pos, "Malformed pathspec: " + fail.msg)
    }

    (pathElements, hasStar)
  }

  // "(/([^/]*)|{ClassName})+"
  def impl[U: c.WeakTypeTag](c: Context)(priority: c.Expr[Long], pathSpec: c.Expr[String])(targetObject: c.Expr[Any]): c.Expr[PathTree[String, U]] = {
    import c.universe._

    val (pathElements, hasStar) = parsePathInfo(c)(pathSpec)

    // The output of
    //   path[U](9999, "/a/b/{String}/{Int}/c") { (s: String, i: Int) => BLAH }
    // is
    //   locally {
    //     val priority: Long = 9999
    //     val f: (String, Int) => U = { (s: String, i: Int) => BLAH }
    //     val p: List[Any] => U = { ss => f(ss(0).asInstanceOf[String], ss(1).asInstanceOf[Int]) }
    //     val terminus = PathTree.fixRoot[String](priority, Nil))
    //     val p1 = new PathTree[String, List[Any]](Map("c" -> terminus, Nil, Map.empty)
    //     val p2 = new PathTree[String, List[Any]](Map.empty, List(Extract[Int](p1)), Map.empty)
    //     val p3 = new PathTree[String, List[Any]](Map.empty, List(Extract[String](p2)), Map.empty)
    //     val p4 = new PathTree[String, List[Any]](Map("b" -> p3), Nil, Map.empty)
    //     val p5 = new PathTree[String, List[Any]](Map("a" -> p4), Nil, Map.empty)
    //     p5.mapStrict(p)
    //   }
    //
    // The output of
    //   path[U](9999, "/a/b/{String}/{Int}/c/*") { (s: String, i: Int, xs: LinearSeq[String]) => BLAH }
    // is
    //   locally {
    //     val priority: Long = 9999
    //     val f: (String, Int, LinearSeq[String]) => U = { (s: String, i: Int, xs: LinearSeq[String]) => BLAH }
    //     val typeify: List[Any] => U = { ss => f(ss(0).asInstanceOf[String], ss(1).asInstanceOf[Int], ss(2).asInstanceOf[LinearSeq[String]]) }
    //     val terminus = PathTree.flexRoot[String](priority, _ :: Nil))
    //     val p1 = new PathTree[String, List[Any]](Map("c" -> terminus, Nil, Map.empty)
    //     val p2 = new PathTree[String, List[Any]](Map.empty, List(Extract[Int](p1)), Map.empty)
    //     val p3 = new PathTree[String, List[Any]](Map.empty, List(Extract[String](p2)), Map.empty)
    //     val p4 = new PathTree[String, List[Any]](Map("b" -> p3), Nil, Map.empty)
    //     val p5 = new PathTree[String, List[Any]](Map("a" -> p4), Nil, Map.empty)
    //     p5.mapStrict(typeify)
    //   }
    //
    // aux functions needed:
    //   def Extract[P, T](p: PathTree[P, List[Any]])(implicit val converter: Extracter[T]): String => Option[PathTree[String, List[Any]]] = { s =>
    //     converter.convert(s).map { r => p.map(r :: _) }
    //   }
    //
    // With luck, untyped macros will eventually let the type annotations on the action func and the
    // return go away.

    def typeFromName(s: String) = {
      val TypeDef(_, _, _, rhs) = c.parse("type T = " + s)
      rhs
    }

    def termFromName(s: String) = {
      val ValDef(_, _, _, rhs) = c.parse("val x = " + s)
      rhs
    }

    val types = pathElements.collect {
      case PathInstance(className) => typeFromName(className)
      case PathTypedInstance(className, _, true) => tq"_root_.com.socrata.http.server.routing.TypedPathComponent[${typeFromName(className)}]"
      case PathTypedInstance(className, _, false) => tq"_root_.com.socrata.http.server.routing.OptionallyTypedPathComponent[${typeFromName(className)}]"
    } ++ (if(hasStar) List(tq"_root_.scala.collection.immutable.LinearSeq[_root_.scala.Predef.String]") else Nil)

    val uTree = TypeTree(weakTypeOf[U])

    val priorityName = newTermName(c.fresh("priority"))
    val priorityTree = q"val $priorityName : _root_.scala.Long = $priority"

    val fName = newTermName(c.fresh("f"))
    val fTree = locally {
      val argCount = types.length
      if(argCount == 0) {
        val targetObjectName = newTermName(c.fresh("target"))
        List(
          q"val $targetObjectName : $uTree = $targetObject",
          q"val $fName: Function0[$uTree] = () => $targetObjectName")
      } else {
        val typeName = typeFromName(s"_root_.scala.Function" + argCount)
        val typeParams = types :+ uTree
        List(q"val $fName: $typeName[..$typeParams] = $targetObject")
      }
    }

    val typeifyName = newTermName(c.fresh("typeify"))
    val pTree = locally {
      val ssName = newTermName(c.fresh("ss"))
      val itName = newTermName(c.fresh("id"))
      val args = types.map { t => q"$itName.next().asInstanceOf[$t]" }
      q"val $typeifyName: _root_.scala.collection.immutable.List[_root_.scala.Any] => $uTree = { $ssName => val $itName = $ssName.iterator; $fName(..$args) }"
    }

    val terminus = newTermName(c.fresh("terminus"))
    val terminusTree = if(hasStar) {
      q"val $terminus = _root_.com.socrata.http.server.routing.PathTree.flexRoot[_root_.scala.Predef.String, _root_.scala.collection.immutable.List[_root_.scala.Any]]($priorityName, _ :: _root_.scala.collection.immutable.Nil)"
    } else {
      q"val $terminus = _root_.com.socrata.http.server.routing.PathTree.fixRoot[_root_.scala.Predef.String][_root_.scala.collection.immutable.List[_root_.scala.Any]]($priorityName, _root_.scala.collection.immutable.Nil)"
    }

    val (lastP, pRev) = pathElements.foldRight((terminus, List.empty[Tree])) { (pathElement, acc) =>
      val (lastP, termsSoFar) = acc
      val pN = newTermName(c.fresh("p"))
      val (lit, funcy) = pathElement match {
        case PathLiteral(literal) =>
          (q"_root_.scala.collection.immutable.Map($literal -> $lastP)", q"_root_.scala.collection.immutable.Nil")
        case PathInstance(className) =>
          val cls = typeFromName(className)
          (q"_root_.scala.collection.immutable.Map.empty", q"_root_.scala.collection.immutable.List(_root_.com.socrata.http.server.`routing-impl`.Extract[$cls]($lastP))")
        case PathTypedInstance(className, regexName, required) =>
          val cls = typeFromName(className)
          val re = termFromName(regexName)
          val extractor =
            if(required) q"_root_.com.socrata.http.server.`routing-impl`.Extract.typedExtractor[$cls]($re)"
            else q"_root_.com.socrata.http.server.`routing-impl`.Extract.optionallyTypedExtractor[$cls]($re)"
          (q"_root_.scala.collection.immutable.Map.empty", q"_root_.scala.collection.immutable.List(_root_.com.socrata.http.server.`routing-impl`.Extract.explicit($extractor, $lastP))")
      }
      val newTerm = q"val $pN = new _root_.com.socrata.http.server.routing.PathTree[_root_.scala.Predef.String, _root_.scala.collection.immutable.List[_root_.scala.Any]]($lit, $funcy, _root_.scala.collection.immutable.Map.empty)"
      (pN, newTerm :: termsSoFar)
    }

    val result = q"""{
      $priorityTree
      ..$fTree
      $pTree
      $terminusTree
      ..${pRev.reverse}
      $lastP.mapStrict($typeifyName)
    }"""

    c.Expr[PathTree[String, U]](result)
  }
}

object PathTreeBuilderAux {
  val StandardRegex = ".*".r
}
