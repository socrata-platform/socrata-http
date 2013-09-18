package com.socrata.http.common.util

import scala.annotation.tailrec
import scala.collection.mutable

class Trie[K, +V](private val subtries: Map[K, Trie[K, V]], private val here: Option[V]) {
  def +[V2 >: V](kv: (Iterable[K], V2)) = Trie.augment(this, kv._1, Some(kv._2))

  def -(ks: Iterable[K]) = Trie.augment(this, ks, None)

  def contains(ks: Iterable[K]) = get(ks).isDefined

  def subtrie(k: K): Option[Trie[K, V]] = subtries.get(k)
  def subtrie(ks: Iterable[K]): Option[Trie[K, V]] = {
    var here = this
    val it = ks.iterator
    while(it.hasNext) {
      val k = it.next()
      here.subtrie(k) match {
        case Some(st) => here = st
        case None => return None
      }
    }
    Some(here)
  }

  def longestPrefix(ks: Iterable[K]): Option[Seq[K]] = {
    var here = this
    var foundAny = here.here.isDefined
    var pfxPtr = 0
    val maybePfx= Vector.newBuilder[K]
    val it = ks.iterator
    var ctr = 0
    while(it.hasNext) {
      val k = it.next()
      here.subtrie(k) match {
        case Some(st) =>
          maybePfx += k
          if(st.here.isDefined) {
            foundAny = true
            pfxPtr = ctr
          }
          here = st
        case None =>
          if(foundAny) return Some(maybePfx.result().take(pfxPtr))
          else return None
      }
      ctr += 1
    }
    if(foundAny) Some(maybePfx.result().take(pfxPtr))
    else None
  }

  def nearest(ks: Iterable[K]): Option[V] = {
    var here = this
    var lastV = here.here

    val it = ks.iterator
    while(it.hasNext) {
      val k = it.next()
      here.subtrie(k) match {
        case Some(st) =>
          here = st
          if(here.here.isDefined) lastV = here.here
        case None =>
          return lastV
      }
    }

    lastV
  }

  val size: Int = {
    var total = if(here.isDefined) 1 else 0
    val it = subtries.valuesIterator
    while(it.hasNext) total += it.next().size
    total
  }

  def get(ks: Iterable[K]): Option[V] = {
    val it = ks.iterator
    var here = this
    while(it.hasNext) {
      val k = it.next()
      here.subtries.get(k) match {
        case Some(t) => here = t
        case None => return None
      }
    }
    here.here
  }

  def iterator: Iterator[(List[K], V)] = {
    val childIts: Iterator[(List[K], V)] = subtries.iterator.flatMap { case (k, subtrie) =>
      subtrie.iterator.map { case (ks, v) => (k :: ks, v) }
    }
    here match {
      case None => childIts
      case Some(v) => Iterator.single((Nil, v)) ++ childIts
    }
  }

  override def toString = iterator.mkString("Trie(", ",", ")")
}

object Trie {
  private val EMPTY = new Trie[Nothing,Nothing](Map.empty, None)
  def empty[K, V] = EMPTY.asInstanceOf[Trie[K, V]]

  def apply[K,V](xs: (Seq[K], V)*) = xs.foldLeft(empty[K, V])(_ + _)

  private def augment[K, V](start: Trie[K, V], ks: Iterable[K], v: Option[V]): Trie[K, V] = {
    val it = ks.iterator
    var here = start
    var upPath = List.empty[(K, Trie[K, V])]

    while(it.hasNext) {
      val k = it.next()
      here.subtries.get(k) match {
        case None =>
          v match {
            case newValue@Some(_) => return newTrie(it, newValue, (k, here) :: upPath)
            case _ => return start // abort abort -- removing a node that doesn't exist
          }
        case Some(subtrie) =>
          upPath = (k, here) :: upPath
          here = subtrie
      }
    }

    if(here.subtries.isEmpty && v.isEmpty) unrollRemove(upPath)
    else unrollAdd(upPath, new Trie(here.subtries, v))
  }

  private def newTrie[K, V](it: Iterator[K], newValue: Some[V], upPathInit: List[(K, Trie[K, V])]): Trie[K, V] = {
    var upPath = upPathInit
    while(it.hasNext) {
      upPath = (it.next(), Trie.empty[K, V]) :: upPath
    }
    unrollAdd(upPath, new Trie[K, V](Map.empty, newValue))
  }

  @tailrec
  private def unrollAdd[K, V](upPath: List[(K, Trie[K, V])], node: Trie[K, V]): Trie[K, V] = upPath match {
    case (k, trie) :: tl =>
      val newMap = trie.subtries + (k -> node)
      unrollAdd(tl, new Trie(newMap, trie.here))
    case Nil =>
      node
  }

  @tailrec
  private def unrollRemove[K, V](upPath: List[(K, Trie[K, V])]): Trie[K, V] = upPath match {
    case (k, trie) :: tl =>
      val newMap = trie.subtries - k
      if(newMap.isEmpty && trie.here.isEmpty) unrollRemove(tl)
      else unrollAdd(tl, new Trie(newMap, trie.here))
    case Nil =>
      Trie.empty[K, V]
  }
}
