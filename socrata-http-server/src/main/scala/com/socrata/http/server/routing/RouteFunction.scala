package com.socrata.http.server.routing

/** The leaf value stored in a `PathTree` by the `Route` macros.  It behaves exactly
  * like the plain `List[Any] => U` function it wraps, but additionally remembers where
  * it came from so that tooling (e.g. OpenAPI generation) can introspect a route tree.
  *
  * @param pathSpec the path pattern the route was registered with, e.g. `/v1/widgets/{Int}`
  * @param target the object passed to `Route`.  For routes with no path parameters this is
  *               the service/resource itself; otherwise it is the function that builds one
  *               from the parsed path parameters.
  * @param attributes arbitrary opt-in metadata attached with `PathTree#withAttribute`.
  */
final class RouteFunction[+U](val pathSpec: String,
                              val target: Any,
                              val attributes: Vector[Any],
                              f: List[Any] => U) extends (List[Any] => U) {
  def this(pathSpec: String, target: Any, f: List[Any] => U) = this(pathSpec, target, Vector.empty, f)

  def apply(args: List[Any]): U = f(args)

  def withAttribute(attribute: Any): RouteFunction[U] =
    new RouteFunction(pathSpec, target, attributes :+ attribute, f)

  override def toString = s"RouteFunction($pathSpec)"
}
