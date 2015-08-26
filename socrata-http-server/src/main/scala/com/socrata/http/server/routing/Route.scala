package com.socrata.http.server.routing

import scala.language.experimental.macros

import com.socrata.http.server.`routing-impl`.RouteImpl
import com.socrata.http.server.{HttpRequest, HttpService, HttpResponse}

trait IsHttpService[+Service] {
  def wrap(s: HttpService): Service
}

object IsHttpService {
  @inline def apply[U](implicit s: IsHttpService[U]): s.type = s

  implicit object Identity extends IsHttpService[HttpService] {
    def wrap(s: HttpService) = s
  }

  type HSR = HttpRequest

  implicit def T1[A] =
    new IsHttpService[((A, HSR)) => HttpResponse] {
      def wrap(s: HttpService) = { case (_, req) => s(req) }
    }

  implicit def T2[A, B] =
    new IsHttpService[((A, B, HSR)) => HttpResponse] {
      def wrap(s: HttpService) = { case (_, _, req) => s(req) }
    }

  implicit def T3[A, B, C] =
    new IsHttpService[((A, B, C, HSR)) => HttpResponse] {
      def wrap(s: HttpService) = { case (_, _, _, req) => s(req) }
    }

  implicit def T5[A, B, C, D] =
    new IsHttpService[((A, B, C, D, HSR)) => HttpResponse] {
      def wrap(s: HttpService) = { case (_, _, _, _, req) => s(req) }
    }
}

class RouteContext[From, To] {
  type Service = com.socrata.http.server.Service[From, To]

  def Routes(x: PathTree[List[Any] => Service], xs: PathTree[List[Any] => Service]*): PathTree[List[Any] => Service] =
    xs.foldLeft(x)(_ merge _)

  def Route(pathSpec: String, targetObject: Any): PathTree[List[Any] => Service] =
    macro RouteImpl.route[From, To]

  def Directory(pathSpec: String)(implicit ihs: IsHttpService[Service]): PathTree[List[Any] => Service] =
    macro RouteImpl.dir[From, To]
}

object SimpleRouteContext extends RouteContext[HttpRequest, HttpResponse]
