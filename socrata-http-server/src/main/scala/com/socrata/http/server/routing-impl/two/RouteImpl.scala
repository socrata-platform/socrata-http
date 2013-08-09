package com.socrata.http.server.`routing-impl`.two

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.util.parsing.combinator.RegexParsers
import scala.collection.immutable.LinearSeq
import com.socrata.http.server.routing.two.PathTree

object RouteImpl {
  sealed abstract class ComponentType
  case class PathInstance(className: String) extends ComponentType
  case class PathLiteral(value: String) extends ComponentType

  private val priorityProvider = new java.util.concurrent.atomic.AtomicLong(0L)
  def nextPriority() = priorityProvider.getAndIncrement()

  class Parser extends RegexParsers {
    val classNameComponent = "[a-zA-Z0-9_]+".r
    val dot = "."
    val className = "{" ~> rep1sep(classNameComponent, dot) <~ "}" ^^ { xs => xs.mkString(".") }
    val lit = "[^{/*][^/]*".r
    val slash = "/"
    val star = '*'
    val dir = slash ~> ((lit ^^ PathLiteral) ||| (className ^^ PathInstance))
    val path = rep1(dir) ~ opt(slash ~ star) ^^ { case a ~ b => (a, b.isDefined) }
  }

  // "(/([^/]*)|{ClassName})+"
  def impl[U: c.WeakTypeTag](c: Context)(pathSpec: c.Expr[String])(targetObject: c.Expr[Any]): c.Expr[PathTree[String, U]] = {
    import c.universe._

    val path = pathSpec.tree match {
      case Literal(Constant(s: String)) => s
      case other => c.abort(other.pos, "The `pathSpec' arrgument to a SmartRoute must be a string literal")
    }

    val parser = new Parser
    val (pathElements, hasStar) = parser.parseAll(parser.path, path) match {
      case parser.Success(elems, _) => elems : (Seq[ComponentType], Boolean)
      case fail: parser.NoSuccess => c.abort(pathSpec.tree.pos, "Malformed pathspec: " + fail.msg)
    }

    // The output of
    //   path[U]("/a/b/{String}/{Int}/c") { (s: String, i: Int) => BLAH }
    // is
    //   locally {
    //     val f: (String, Int) => U = { (s: String, i: Int) => BLAH }
    //     val p: List[Any] => U = { ss => f(ss(0).asInstanceOf[String], ss(1).asInstanceOf[Int]) }
    //     val terminus = PathTree.fixRoot[String](nextPriority(), Nil))
    //     val p1 = new PathTree[String, List[Any]](Map("c" -> terminus, Nil, Map.empty)
    //     val p2 = new PathTree[String, List[Any]](Map.empty, List(Extract[Int](p1)), Map.empty)
    //     val p3 = new PathTree[String, List[Any]](Map.empty, List(Extract[String](p2)), Map.empty)
    //     val p4 = new PathTree[String, List[Any]](Map("b" -> p3), Nil, Map.empty)
    //     val p5 = new PathTree[String, List[Any]](Map("a" -> p4), Nil, Map.empty)
    //     p5.mapStrict(p)
    //   }
    //
    // The output of
    //   path[U]("/a/b/{String}/{Int}/c/*") { (s: String, i: Int, xs: LinearSeq[String]) => BLAH }
    // is
    //   locally {
    //     val f: (String, Int, LinearSeq[String]) => U = { (s: String, i: Int, xs: LinearSeq[String]) => BLAH }
    //     val typeify: List[Any] => U = { ss => f(ss(0).asInstanceOf[String], ss(1).asInstanceOf[Int], ss(2).asInstanceOf[LinearSeq[String]]) }
    //     val terminus = PathTree.flexRoot[String](nextPriority(), _ :: Nil))
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

    val types = pathElements.collect {
      case PathInstance(className) => typeFromName(className)
    } ++ (if(hasStar) List(tq"_root_.scala.collection.immutable.LinearSeq[_root_.scala.Predef.String]") else Nil)

    val uTree = TypeTree(weakTypeOf[U])

    val fName = newTermName(c.fresh("f"))
    val fTree = locally {
      val argCount = types.length
      val typeName = typeFromName(s"_root_.scala.Function" + argCount)
      val typeParams = types :+ uTree
      q"val $fName: $typeName[..$typeParams] = $targetObject"
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
      q"val $terminus = _root_.com.socrata.http.server.routing.two.PathTree.flexRoot[_root_.scala.Predef.String, _root_.scala.collection.immutable.List[_root_.scala.Any]](_root_.com.socrata.http.server.`routing-impl`.two.RouteImpl.nextPriority(), _ :: _root_.scala.collection.immutable.Nil)"
    } else {
      q"val $terminus = _root_.com.socrata.http.server.routing.two.PathTree.fixRoot[_root_.scala.Predef.String][_root_.scala.collection.immutable.List[_root_.scala.Any]](_root_.com.socrata.http.server.`routing-impl`.two.RouteImpl.nextPriority(), _root_.scala.collection.immutable.Nil)"
    }

    val (lastP, pRev) = pathElements.foldRight((terminus, List.empty[Tree])) { (pathElement, acc) =>
      val (lastP, termsSoFar) = acc
      val pN = newTermName(c.fresh("p"))
      val (lit, funcy) = pathElement match {
        case PathLiteral(literal) =>
          (q"_root_.scala.collection.immutable.Map($literal -> $lastP)", q"_root_.scala.collection.immutable.Nil")
        case PathInstance(className) =>
          val cls = typeFromName(className)
          (q"_root_.scala.collection.immutable.Map.empty", q"_root_.scala.collection.immutable.List(_root_.com.socrata.http.server.`routing-impl`.two.Extract[$cls]($lastP))")
      }
      val newTerm = q"val $pN = new _root_.com.socrata.http.server.routing.two.PathTree[_root_.scala.Predef.String, _root_.scala.collection.immutable.List[_root_.scala.Any]]($lit, $funcy, _root_.scala.collection.immutable.Map.empty)"
      (pN, newTerm :: termsSoFar)
    }

    val result = q"""{
      $fTree
      $pTree
      $terminusTree
      ..${pRev.reverse}
      $lastP.mapStrict($typeifyName)
    }"""

    println(result)

    c.Expr[PathTree[String, U]](result)
  }
}
