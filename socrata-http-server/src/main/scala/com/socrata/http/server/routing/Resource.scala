package com.socrata.http.server.routing

import com.socrata.http.server.{Service, HttpService, HttpResponse}
import com.socrata.http.server.responses._
import javax.servlet.http.HttpServletRequest

import scala.language.higherKinds

trait Resource[From, To] extends Service[From, To] {
  def get: Service[From, To] = methodNotAllowed
  def post: Service[From, To] = methodNotAllowed
  def put: Service[From, To] = methodNotAllowed
  def delete: Service[From, To] = methodNotAllowed
  def patch: Service[From, To] = methodNotAllowed
  def unknownMethod(method: String): Service[From, To] = methodNotAllowed

  def methodNotAllowed: Service[From, To]
  def methodOf(f: From): String

  def apply(req: From): To = {
    val serv = methodOf(req) match {
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

trait SimpleResource extends Resource[HttpServletRequest, HttpResponse] {
  def methodNotAllowed = SimpleResource.defaultMethodNotAllowed
  def methodOf(req: HttpServletRequest) = req.getMethod
}

object SimpleResource {
  val defaultMethodNotAllowed: HttpService = _ => MethodNotAllowed
}
