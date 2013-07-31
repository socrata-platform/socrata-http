package com.socrata.http.`-impl`

import scala.annotation.tailrec

private[http] class IntrusivePriorityQueue[T <: IntrusivePriorityQueueNode] {
  private[this] var nodes = new Array[IntrusivePriorityQueueNode](16)
  private[this] var endptr = 0

  def add(node: T) {
    add(node, node.priority)
  }

  def add(node: T, priority: Long) {
    if(node.pq_queue ne null) throw new IllegalArgumentException("Node is already in a queue")
    if(endptr == nodes.length) grow()

    nodes(endptr) = node
    node.pq_queue = this
    node.pq_priorityNoReheap(priority)
    node.pq_idx = endptr
    endptr += 1
    heapUp(endptr - 1, priority)
  }

  // checks the heap invariants
  def isValid: Boolean = {
    def validateAt(idx: Int): Boolean = {
      val childIdxL = (idx << 1) + 1
      if(childIdxL < endptr) {
        if(nodes(idx).priority > nodes(childIdxL).priority) return false
        if(!validateAt(childIdxL)) return false
        val childIdxR = childIdxL + 1
        if(childIdxR < endptr) {
          if(nodes(idx).priority > nodes(childIdxR).priority) return false
          if(!validateAt(childIdxR)) return false
        }
      }
      true
    }
    if(nonEmpty) validateAt(0)
    else true
  }

  def contains(node: T) = node.pq_queue eq this

  def size = endptr

  def remove(node: T): Boolean = {
    if(node.pq_queue ne this) return false

    val idx = node.pq_idx
    endptr -= 1
    if(idx == endptr) {
    } else {
      val oldLast = nodes(endptr)
      nodes(idx) = oldLast
      oldLast.pq_idx = idx
      heapDown(idx, oldLast.priority)
    }
    nodes(endptr) = null
    node.pq_queue = null
    true
  }

  def isEmpty = endptr == 0
  def nonEmpty = endptr != 0

  def head =
    if(isEmpty) throw new NoSuchElementException("empty queue")
    else nodes(0).asInstanceOf[T]

  def pop() : T = {
    if(isEmpty) throw new NoSuchElementException("empty queue")
    val result = nodes(0)
    result.pq_queue = null
    result.pq_idx = -1

    endptr -= 1
    if(endptr != 0) {
      val oldlast = nodes(endptr)
      oldlast.pq_idx = 0
      nodes(0) = oldlast
      heapDown(0, oldlast.priority)
    }
    nodes(endptr) = null

    result.asInstanceOf[T]
  }

  private def grow() {
    val newNodes = new Array[IntrusivePriorityQueueNode](nodes.length << 1)
    System.arraycopy(nodes, 0, newNodes, 0, nodes.length)
    nodes = newNodes
  }

  private[`-impl`] def reheap(idx: Int, priority: Long) {
    val parentIdx = (idx - 1) >> 1
    if(parentIdx >= 0 && nodes(parentIdx).priority > priority) {
      swap(idx, parentIdx)
      heapUp(parentIdx, priority)
    } else {
      heapDown(idx, priority)
    }
  }

  @tailrec
  private def heapUp(idx: Int, priority: Long) {
    val parentIdx = (idx - 1) >> 1
    if(parentIdx >= 0 && nodes(parentIdx).priority > priority) {
      swap(idx, parentIdx)
      heapUp(parentIdx, priority)
    }
  }

  @tailrec
  private def heapDown(idx: Int, priority: Long) {
    val childIdxL = (idx << 1) + 1
    if(childIdxL < endptr) {
      val lPri = nodes(childIdxL).priority
      val childIdxR = childIdxL + 1
      if(childIdxR < endptr) {
        val rPri = nodes(childIdxR).priority
        if(lPri < rPri) {
          if(lPri < priority) {
            swap(idx, childIdxL)
            heapDown(childIdxL, priority)
          }
        } else if(rPri < priority) {
          swap(idx, childIdxR)
          heapDown(childIdxR, priority)
        }
      } else {
        if(lPri < priority) {
          swap(idx, childIdxL)
          // "idx" has no right child; therefore childIdx has no
          // children at all and we're done.
        }
      }
    }
  }

  private def swap(idx1: Int, idx2: Int) {
    val node1 = nodes(idx1)
    node1.pq_idx = idx2

    val node2 = nodes(idx2)
    node2.pq_idx = idx1

    nodes(idx1) = node2
    nodes(idx2) = node1
  }

  override def toString = nodes.iterator.take(endptr).mkString("[",", ","]")
}

private[http] abstract class IntrusivePriorityQueueNode {
  private[`-impl`] var pq_queue: IntrusivePriorityQueue[_] = null
  private[this] var pq_priority: Long = 0L
  private[`-impl`] var pq_idx: Int = -1
  private[`-impl`] def pq_priorityNoReheap(priority: Long) {
    pq_priority = priority
  }

  def priority = pq_priority
  def priority_=(priority: Long) {
    pq_priority = priority
    if(pq_queue ne null) pq_queue.reheap(pq_idx, pq_priority)
  }

  override def toString = pq_idx + " -> " + pq_priority
}
