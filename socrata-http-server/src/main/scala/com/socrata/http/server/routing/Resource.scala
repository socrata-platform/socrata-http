package com.socrata.http.server.routing

import com.socrata.http.server.{HttpService, HttpResponse}
import com.socrata.http.server.responses._
import javax.servlet.http.HttpServletRequest

trait Resource extends HttpService {
  def get: HttpService = methodNotAllowed
  def post: HttpService = methodNotAllowed
  def put: HttpService = methodNotAllowed
  def delete: HttpService = methodNotAllowed
  def patch: HttpService = methodNotAllowed
  def unknownMethod(method: String): HttpService = methodNotAllowed

  def methodNotAllowed: HttpService = Resource.defaultMethodNotAllowed

  def apply(req: HttpServletRequest): HttpResponse = {
    val serv = req.getMethod match {
      case HttpMethods.GET => get
      case HttpMethods.POST => post
      case HttpMethods.PUT => put
      case HttpMethods.DELETE => delete
      case HttpMethods.PATCH => patch
      case other => unknownMethod(other)
    }
    serv(req)
  }
}

object Resource {
  val defaultMethodNotAllowed: HttpService = _ => MethodNotAllowed
}

trait SingletonResource extends Resource with (() => Resource) {
  def apply() = this
}
