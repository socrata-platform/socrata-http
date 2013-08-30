package com.socrata.http.server.routing

import PathTree2._
import scala.collection.mutable.ListBuffer
import com.socrata.http.server.routing.Matcher.StringMatcher

sealed trait PathTree2[+R] {
  def check(pathComponent: String): Vector[Result[R]]
  def acceptFix: Option[R]
  def acceptFlex: Option[R]

  def apply[U](path: List[String])(implicit ev: R <:< (List[Any] => U)): Option[U] = accept(path).map { case (args, f) => f(args) }

  def merge[R2 >: R](that: PathTree2[R2]): PathTree2[R2]

  def accept(path: List[String]): Option[(List[Any], R)] = {
    case class State(builtSoFarRev: List[Any], nextState: PathTree2[R])
    var states = List(State(Nil, this))
    var fixedAccept: Option[(List[Any], R)] = acceptFix.flatMap { case value =>
      if(path.isEmpty) Some((Nil, value)) else None
    }
    var flexAccept: Option[(List[Any], R)] = acceptFlex.flatMap { case value =>
      if(path.nonEmpty) Some((path.tail :: Nil, value)) else None
    }

    def finish(result: Option[(List[Any], R)]) = result match {
      case Some(x) => Some((x._1.reverse, x._2))
      case None => None
    }

    def updateAccepts(remainingPath: List[String], states: Seq[State]) {
      // Ok, this is a LITTLE weird.  Basically, we can be in more than one state at once
      // and we need to keep track of the rightmost flex match as well as the rightmost
      // fixed match that hasn't been superceded by a more-rightward flex match.  Basically,
      // what we're implementing here is "rightmost match wins" but we also need to keep
      // the last flex match around in case we need to loop.
      var nextFixed: Option[(List[Any], R)] = None
      var nextFlex: Option[(List[Any], R)] = flexAccept
      states.foreach { s =>
        s.nextState.acceptFlex.foreach { case v =>
          if(remainingPath.nonEmpty) { nextFixed = None; nextFlex = Some((remainingPath :: s.builtSoFarRev, v)) }
        }
        s.nextState.acceptFix.foreach { case v =>
          if(remainingPath.isEmpty) nextFixed = Some((s.builtSoFarRev, v))
        }
      }
      fixedAccept = nextFixed
      flexAccept = nextFlex
    }

    var remainingPath = path
    while(remainingPath.nonEmpty && states.nonEmpty) {
      val pathComponent = remainingPath.head
      val nextStates = new ListBuffer[State]
      for(s <- states) {
        for(result <- s.nextState.check(pathComponent)) {
          result match {
            case ExtractResult(value, child) => nextStates += State(value :: s.builtSoFarRev, child)
            case MatchResult(child) => nextStates += State(s.builtSoFarRev, child)
          }
        }
      }
      updateAccepts(remainingPath, states)
      states = nextStates.toList
      remainingPath = remainingPath.tail
    }

    updateAccepts(remainingPath, states)

    finish(fixedAccept orElse flexAccept)
  }
}

trait MatcherLike[T] {
  def matcher(x: T): Matcher
}
object MatcherLike {
  implicit object StringMatcherLike extends MatcherLike[String] {
    def matcher(s: String) = new Matcher.StringMatcher(s)
  }
  implicit object MatcherMatcherLike extends MatcherLike[Matcher] {
    def matcher(m: Matcher) = m
  }
}

object PathTree2 {
  def apply[R, M](elems: Seq[M], result: R, flex: Boolean)(implicit ev: MatcherLike[M]) = {
    val root: PathTree2[R] = new LiteralOnlyPathTree2(Map.empty, if(flex) None else Some(result), if(flex) Some(result) else None)
    elems.foldRight(root) { (elem, child) =>
      ev.matcher(elem) match {
        case s: StringMatcher => new LiteralOnlyPathTree2(Map(s.target -> child), None, None)
        case other => new MatchingPathTree2(List(other -> child), None, None)
      }
    }
  }

  def fixRoot[R](result: R) = new LiteralOnlyPathTree2(Map.empty, Some(result), None)
  def flexRoot[R](result: R) = new LiteralOnlyPathTree2(Map.empty, None, Some(result))

  val empty: PathTree2[Nothing] = new LiteralOnlyPathTree2(Map.empty, None, None)

  sealed abstract class Result[+R]
  final case class ExtractResult[+R](value: Any, child: PathTree2[R]) extends Result[R]
  final case class MatchResult[+R](children: PathTree2[R]) extends Result[R]
  final case class Acceptance[+R](value: R, flexible: Boolean)
}

final case class LiteralOnlyPathTree2[+R](branches: Map[String, PathTree2[R]], acceptFix: Option[R], acceptFlex: Option[R]) extends PathTree2[R] {
  def check(pathComponent: String): Vector[Result[R]] =
    branches.get(pathComponent) match {
      case None => Vector.empty
      case Some(child) => Vector.empty :+ MatchResult(child)
    }

  def merge[R2 >: R](that: PathTree2[R2]): PathTree2[R2] = that match {
    case lo: LiteralOnlyPathTree2[R2] =>
      var b: Map[String, PathTree2[R2]] = branches
      for(kp@(k, p2) <- lo.branches) {
        branches.get(k) match {
          case Some(p1) => b += (k -> p1.merge(p2))
          case None => b += kp
        }
      }
      new LiteralOnlyPathTree2(b, that.acceptFix orElse this.acceptFix, that.acceptFlex orElse this.acceptFlex)
    case mp: MatchingPathTree2[R2] =>
      toMatchingPathTree.merge(that)
  }

  def toMatchingPathTree =
    new MatchingPathTree2[R](branches.iterator.map { case (k,v) => (new Matcher.StringMatcher(k), v) }.toList, acceptFix, acceptFlex)
}

final case class MatchingPathTree2[+R](branches: List[(Matcher, PathTree2[R])], acceptFix: Option[R], acceptFlex: Option[R]) extends PathTree2[R] {
  def check(pathComponent: String): Vector[Result[R]] = {
    val result = Vector.newBuilder[Result[R]]
    for((m, child) <- branches) {
      m match {
        case extractor: Extractor[Any] =>
          extractor.extract(pathComponent) match {
            case Some(extracted) => result += ExtractResult(extracted, child)
            case None => // nothing
          }
        case matcher =>
          if(matcher.matches(pathComponent)) {
            result += MatchResult(child)
          }
      }
    }
    result.result()
  }

  def merge[R2 >: R](that: PathTree2[R2]): PathTree2[R2] = that match {
    case mp: MatchingPathTree2[R2] =>
      trueMerge(mp)
    case lo: LiteralOnlyPathTree2[R2] =>
      trueMerge(lo.toMatchingPathTree)
  }

  private def trueMerge[R2 >: R](that: MatchingPathTree2[R2]): PathTree2[R2] = {
    val newBranches =
      if(this.branches.nonEmpty && that.branches.nonEmpty) {
        val thisLast = this.branches.last
        val thatFirst = that.branches.head

        if(thisLast._1 == thatFirst._1) {
          this.branches.dropRight(1) ++ ((thisLast._1, thisLast._2.merge(thatFirst._2)) +: that.branches.drop(1))
        } else {
          this.branches ::: that.branches
        }
      } else {
        this.branches ::: that.branches
      }
    new MatchingPathTree2[R2](newBranches, that.acceptFix orElse this.acceptFix, that.acceptFlex orElse this.acceptFlex)
  }
}
