package com.socrata.http.server.routing

import PathTree._
import scala.collection.mutable.ListBuffer
import com.socrata.http.server.routing.Matcher.StringMatcher

sealed trait PathTree[+R] {
  def check(pathComponent: String): Vector[Result[R]]
  def acceptFix: Option[R]
  def acceptFlex: Option[R]

  def apply[U](path: List[String])(implicit ev: R <:< (List[Any] => U)): Option[U] =
    accept(path).map { case (args, f) => f(args) }

  def merge[R2 >: R](that: PathTree[R2]): PathTree[R2]

  def accept(path: List[String]): Option[(List[Any], R)] = {
    class Acceptor {
      private[this] class State(val builtSoFarRev: List[Any], val nextState: PathTree[R])

      private[this] var fixedAccept: (List[Any], R) =
        acceptFix match {
          case Some(value) =>
            if(path.isEmpty) (Nil, value) else null
          case None =>
            null
        }
      private[this] var flexAccept: (List[Any], R) =
        acceptFlex match {
          case Some(value) =>
            if(path.nonEmpty) (path.tail :: Nil, value) else null
          case None =>
            null
        }

      def finish(result: (List[Any], R)) =
        if(result eq null) None
        else Some((result._1.reverse, result._2))

      def updateAccepts(remainingPath: List[String], states: List[State]) {
        // Ok, this is a LITTLE weird.  Basically, we can be in more than one state at once
        // and we need to keep track of the rightmost flex match as well as the rightmost
        // fixed match that hasn't been superceded by a more-rightward flex match.  Basically,
        // what we're implementing here is "rightmost match wins" but we also need to keep
        // the last flex match around in case we need to loop.
        var nextFixed: (List[Any], R) = null
        var nextFlex: (List[Any], R) = flexAccept

        var ss = states
        while(ss.nonEmpty) {
          val s = ss.head
          s.nextState.acceptFlex match {
            case Some(v) =>
              if(remainingPath.nonEmpty) {
                nextFixed = null
                nextFlex = (remainingPath :: s.builtSoFarRev, v)
              }
            case _ => // none
          }
          s.nextState.acceptFix match {
            case Some(v) =>
              if(remainingPath.isEmpty) nextFixed = (s.builtSoFarRev, v)
            case _ => // none
          }
          ss = ss.tail
        }
        fixedAccept = nextFixed
        flexAccept = nextFlex
      }

      def go(path: List[String]) = {
        var states = List(new State(Nil, PathTree.this))
        var remainingPath = path

        while(remainingPath.nonEmpty && states.nonEmpty) {
          val pathComponent = remainingPath.head
          val nextStates = new ListBuffer[State]
          var ss = states
          while(ss.nonEmpty) {
            val s = ss.head
            ss = ss.tail

            for(result <- s.nextState.check(pathComponent)) {
              val newState = result match {
                case ExtractResult(value, child) => new State(value :: s.builtSoFarRev, child)
                case MatchResult(child) => new State(s.builtSoFarRev, child)
              }
              nextStates += newState
            }
          }
          updateAccepts(remainingPath, states)
          states = nextStates.toList
          remainingPath = remainingPath.tail
        }

        updateAccepts(remainingPath, states)

        finish(if(fixedAccept eq null) flexAccept else fixedAccept)
      }
    }
    new Acceptor().go(path)
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

object PathTree {
  def apply[R, M](elems: Seq[M], result: R, flex: Boolean)(implicit ev: MatcherLike[M]) = {
    val root: PathTree[R] = new LiteralOnlyPathTree(Map.empty, if(flex) None else Some(result), if(flex) Some(result) else None)
    elems.foldRight(root) { (elem, child) =>
      ev.matcher(elem) match {
        case s: StringMatcher => new LiteralOnlyPathTree(Map(s.target -> child), None, None)
        case other => new MatchingPathTree(List(other -> child), None, None)
      }
    }
  }

  def fixRoot[R](result: R) = new LiteralOnlyPathTree(Map.empty, Some(result), None)
  def flexRoot[R](result: R) = new LiteralOnlyPathTree(Map.empty, None, Some(result))

  val empty: PathTree[Nothing] = new LiteralOnlyPathTree(Map.empty, None, None)

  sealed abstract class Result[+R]
  final case class ExtractResult[+R](value: Any, child: PathTree[R]) extends Result[R]
  final case class MatchResult[+R](children: PathTree[R]) extends Result[R]
}

final case class LiteralOnlyPathTree[+R](branches: Map[String, PathTree[R]], acceptFix: Option[R], acceptFlex: Option[R]) extends PathTree[R] {
  def check(pathComponent: String): Vector[Result[R]] =
    branches.get(pathComponent) match {
      case None => Vector.empty
      case Some(child) => Vector.empty :+ MatchResult(child)
    }

  def merge[R2 >: R](that: PathTree[R2]): PathTree[R2] = that match {
    case lo: LiteralOnlyPathTree[R2] =>
      var b: Map[String, PathTree[R2]] = branches
      for(kp@(k, p2) <- lo.branches) {
        branches.get(k) match {
          case Some(p1) => b += (k -> p1.merge(p2))
          case None => b += kp
        }
      }
      new LiteralOnlyPathTree(b, that.acceptFix orElse this.acceptFix, that.acceptFlex orElse this.acceptFlex)
    case mp: MatchingPathTree[R2] =>
      toMatchingPathTree.merge(that)
  }

  def toMatchingPathTree =
    new MatchingPathTree[R](branches.iterator.map { case (k,v) => (new Matcher.StringMatcher(k), v) }.toList, acceptFix, acceptFlex)
}

final case class MatchingPathTree[+R](branches: List[(Matcher, PathTree[R])], acceptFix: Option[R], acceptFlex: Option[R]) extends PathTree[R] {
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

  def merge[R2 >: R](that: PathTree[R2]): PathTree[R2] = that match {
    case mp: MatchingPathTree[R2] =>
      trueMerge(mp)
    case lo: LiteralOnlyPathTree[R2] =>
      trueMerge(lo.toMatchingPathTree)
  }

  private def trueMerge[R2 >: R](that: MatchingPathTree[R2]): PathTree[R2] = {
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
    new MatchingPathTree[R2](newBranches, that.acceptFix orElse this.acceptFix, that.acceptFlex orElse this.acceptFlex)
  }
}
