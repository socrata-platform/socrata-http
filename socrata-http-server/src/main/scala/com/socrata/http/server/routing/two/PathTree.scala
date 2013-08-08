package com.socrata.http.server.routing.two

import scala.collection.LinearSeq
import scala.annotation.tailrec

class PathTree[P, +R](val literal: Map[P, PathTree[P, R]], val functiony: Iterable[P => Option[PathTree[P, R]]], val terminal : Map[Long, LinearSeq[P] => Option[R]]) extends (LinearSeq[P] => Option[R]) {
  val isEmpty = literal.isEmpty && functiony.isEmpty && terminal.isEmpty
  def nonEmpty = !isEmpty

  def merge[R2 >: R](that: PathTree[P, R2]): PathTree[P, R2] = {
    new PathTree(
      PathTree.mergeLiterals(this.literal, that.literal),
      this.functiony ++ that.functiony,
      this.terminal ++ that.terminal
    )
  }

  def apply(path: LinearSeq[P]): Option[R] =
    PathTree.acceptLoop(this, path, Map.empty)

  def map[S](f: R => S): PathTree[P, S] =
    new PathTree[P, S](
      literal.mapValues(_.map(f)),
      functiony.map(_.andThen(_.map(_.map(f)))),
      terminal.mapValues(_.andThen(_.map(f)))
    )

  // strictly maps literals and terminals as deeply as possible
  def mapStrict[S](f: R => S): PathTree[P, S] =
    new PathTree[P, S](
      literal.mapValues(_.mapStrict(f)).toMap,
      functiony.map(_.andThen(_.map(_.map(f)))),
      terminal.mapValues(_.andThen(_.map(f))).toMap
    )

  override def toString = "PathTree(" + literal + ", " + functiony.iterator.length + " functiony(s), " + terminal + ")"
}

object PathTree {
  private def mergeLiterals[P,R](a: Map[P, PathTree[P, R]], b: Map[P, PathTree[P, R]]): Map[P, PathTree[P, R]] = {
    val result = Map.newBuilder[P, PathTree[P, R]]
    for(kv@(k, v) <- a) {
      if(b.contains(k)) result += k -> v.merge(b(k))
      else result += kv
    }
    for(kv@(k, _) <- b if !a.contains(k)) result += kv
    result.result()
  }

  private def pickFrom[R](candidates: Map[Long, R]): Option[R] =
    if(candidates.isEmpty) None
    else Some(candidates.maxBy(_._1)._2)

  def nextTerminal[P, R](path: LinearSeq[P], possibleTerms: Map[Long, LinearSeq[P] => Option[R]], ifNone: Map[Long, R]): Map[Long, R] = {
    if(possibleTerms.isEmpty) ifNone
    else {
      val builder = Map.newBuilder[Long, R]
      possibleTerms.foreach { case (pri, acceptsRemainingTerm) =>
        acceptsRemainingTerm(path) match {
          case Some(r) => builder += pri -> r
          case _ => // pass
        }
      }
      val res = builder.result()
      if(res.isEmpty) ifNone
      else res
    }
  }

  @tailrec
  private def acceptLoop[P, R](self: PathTree[P, R], path: LinearSeq[P], previousWithWildcard: Map[Long, R]): Option[R] = path match {
    case hd +: tl =>
      val term = nextTerminal(path, self.terminal, previousWithWildcard)
      if(self.nonEmpty) {
        val next = self.functiony.foldLeft(self.literal.getOrElse(hd, PathTree.empty)) { (acc, f) =>
          f(hd).map(acc.merge).getOrElse(acc)
        }
        acceptLoop(next, tl, term)
      } else {
        pickFrom(term)
      }
    case _ =>
      pickFrom(nextTerminal(path, self.terminal, previousWithWildcard))
  }

  private[this] val emptyPT: PathTree[Any, Nothing] = new PathTree[Any, Nothing](Map.empty, Seq.empty, Map.empty) {
    override def map[S](f: Nothing => S) = this
  }
  def empty[P, R] = emptyPT.asInstanceOf[PathTree[P, R]]

  def fixRoot[P] = fixRootProvider.asInstanceOf[FRP[P]]
  class FRP[P] {
    def apply [R](priority: Long, r: R): PathTree[P, R] =
      new PathTree[P, R](Map.empty, Seq.empty, Map(priority -> { f => if(f.isEmpty) Some(r) else None }))
  }
  private val fixRootProvider = new FRP[Any]

  def flexRoot[P, R](priority: Long, r: LinearSeq[P] => R): PathTree[P, R] =
    new PathTree[P, R](Map.empty, Seq.empty, Map(priority -> { xs => Some(r(xs)) }))
}
