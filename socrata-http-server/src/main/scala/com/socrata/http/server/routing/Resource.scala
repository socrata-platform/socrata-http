package com.socrata.http.server.routing

import com.socrata.http.server.{HttpRequest, Service, HttpService, HttpResponse}
import com.socrata.http.server.responses._
import com.socrata.http.server.implicits._
import java.util.Locale

trait Resource[From, To] extends Service[From, To] {

  /* HTTP Method handlers. */

  @Unoverridden
  def connect: Service[From, To] = methodNotAllowed

  @Unoverridden
  def delete: Service[From, To] = methodNotAllowed

  @Unoverridden
  def get: Service[From, To] = methodNotAllowed

  @Unoverridden
  def head: Service[From, To] = methodNotAllowed

  @Unoverridden
  def options: Service[From, To] = methodNotAllowed

  @Unoverridden
  def patch: Service[From, To] = methodNotAllowed

  @Unoverridden
  def post: Service[From, To] = methodNotAllowed

  @Unoverridden
  def put: Service[From, To] = methodNotAllowed

  @Unoverridden
  def trace: Service[From, To] = methodNotAllowed

  /* End HTTP method handlers. */

  def unknownMethod(method: String): Service[From, To] = methodNotAllowed

  def methodNotAllowed: Service[From, To]
  def methodOf(f: From): String

  /** Returns the set of HTTP methods allowed by this object.  The default
    * implementation uses reflection to figure out which of the HTTP methods
    * have been overridden, and assumes that overridden methods are exactly
    * the set of allowed ones.
    *
    * @note This MUST be overridden if the heuristic described above is not correct!
    */
  def allowedMethods: Set[String] = {
    val sb = Set.newBuilder[String]
    val cls = getClass
    def check(name: String) {
      // The "Unoverridden" annotation is required because
      // of the way traits-with-implementations work in Scala.
      // In particular, a concrete implementation of this trait
      // will have a stub impementation with the annotation privded
      // by scalac, but of course an overridden implementation will
      // no longer have the annotation.
      try {
        if(cls.getDeclaredMethod(name.toLowerCase(Locale.US)).getAnnotation(classOf[Unoverridden]) eq null)
          sb += name
      } catch {
        case _: NoSuchMethodException => /* nothing */
      }
    }
    check(HttpMethods.CONNECT)
    check(HttpMethods.DELETE)
    check(HttpMethods.GET)
    check(HttpMethods.HEAD)
    check(HttpMethods.OPTIONS)
    check(HttpMethods.PATCH)
    check(HttpMethods.POST)
    check(HttpMethods.PUT)
    check(HttpMethods.TRACE)
    sb.result()
  }

  def apply(req: From): To = {
    val serv = methodOf(req) match {
      case HttpMethods.CONNECT => connect
      case HttpMethods.DELETE => delete
      case HttpMethods.GET => get
      case HttpMethods.HEAD => head
      case HttpMethods.OPTIONS => options
      case HttpMethods.PATCH => patch
      case HttpMethods.POST => post
      case HttpMethods.PUT => put
      case HttpMethods.TRACE => trace
      case other => unknownMethod(other)
    }
    serv(req)
  }
}

trait SimpleResource extends Resource[HttpRequest, HttpResponse] {
  def methodNotAllowed: HttpService = SimpleResource.defaultMethodNotAllowed(allowedMethods)
  def methodOf(req: HttpRequest) = req.method
}

object SimpleResource {
  def defaultMethodNotAllowed(allowed: Set[String]): HttpService =
    _ => MethodNotAllowed ~> Header("Allow", allowed.mkString(", "))
}
