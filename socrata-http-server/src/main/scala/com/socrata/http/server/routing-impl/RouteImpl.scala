package com.socrata.http.server.`routing-impl`

import scala.language.experimental.macros
import scala.reflect.macros.Context
import com.socrata.http.server.routing.PathTree
import com.socrata.http.server.HttpService

object RouteImpl {
  private val priorityProvider = new java.util.concurrent.atomic.AtomicLong(0L)
  def nextPriority() = priorityProvider.getAndIncrement()

  def impl(c: Context)(pathSpec: c.Expr[String], targetObject: c.Expr[Any]): c.Expr[PathTree[String, HttpService]] = {
    import c.universe._
    val tree = q"_root_.com.socrata.http.server.routing.PathTreeBuilder[_root_.com.socrata.http.server.HttpService](_root_.com.socrata.http.server.`routing-impl`.RouteImpl.nextPriority(), $pathSpec)($targetObject)"
    c.Expr[PathTree[String, HttpService]](tree)
  }
}
