Socrata-HTTP is a toolkit for HTTP client access and building rack-style servers.  The server is Jetty-based, but not Servlet-based, though it does use `HttpServletRequest` and `HttpServletResponse`.

There is a minimal [ExampleService](socrata-http-jetty/src/main/scala/com/socrata/http/server/example/ExampleService.scala).

There is a minimal [sbt project](https://github.com/socrata-platform/socrata-http-hello-world) using this as well.

There is an old example at [TeaService](https://github.com/socrata/socrata-httparty/blob/master/src/main/scala/com/socrata/teaparty/services/TeaService.scala), [TeaRouter](https://github.com/socrata/socrata-httparty/blob/master/src/main/scala/com/socrata/teaparty/handlers/TeaRouter.scala) and [TeaParty](https://github.com/socrata/socrata-httparty/blob/master/src/main/scala/com/socrata/teaparty/TeaParty.scala), but unfortunately only compiles with socrata-http 2.x.x.

## Features

* Zookeeper-based service discovery and registration
* Graceful termination of requests at exit
* Optional OpenAPI spec generation from your routes (see below)

## OpenAPI spec generation

The optional `socrata-http-server-openapi` module generates an OpenAPI 3.0.3 document from your
route tree and can serve it from a path you choose.  It depends only on `socrata-http-server`, so
services that don't use it are unaffected.

### Setup

Add the module to your build:

```scala
libraryDependencies += "com.socrata" %% "socrata-http-server-openapi" % "<version>"
```

Then document your routes and (optionally) serve the spec:

```scala
import com.socrata.http.server.openapi._
import com.socrata.http.server.openapi.OpenApi.RouteDocs   // adds .documented(...)
import com.socrata.http.server.routing.SimpleRouteContext._

val routes = Routes(
  // A Resource with no path params: its HTTP methods are discovered automatically.
  Route("/v1/widgets", widgetsResource),

  // Routes with path params, or whose target isn't a Resource, need a Doc to say what they do.
  Route("/v1/widgets/{Int}", widgetById).documented(
    Doc(
      get = Some(Operation(
        summary = Some("Get a widget"),
        parameters = List(Parameter("expand", schema = Schema.boolean)),
        responses = Map(
          200 -> Response.json("The widget", Schema.ref("Widget")),
          404 -> Response("No such widget")))),
      pathParams = List("id")))   // names for the path params, in order; defaults to param1, param2, ...
)

// Serve the spec.  The path is an ordinary runtime string, so it can come from configuration.
val withSpec = OpenApi.serve(config.specPath, Info("My Service", "1.0"))(routes)
```

Dispatch on `withSpec` exactly as you would on `routes`.  `OpenApi.serve` is the only thing that adds
the endpoint, so **to leave the spec out, don't call it** and use `routes` directly.  The served
document describes `routes` only; it does not list the spec endpoint itself.

If you want the JSON without serving it (to write it to a file in a build step, say), use
`OpenApi.generate(routes, Info(...), servers = Seq(Server("https://example.com")))`, which returns a
rojoma-json `JObject`.

### Example: a service with a routing table

Most services keep their route tree in a field and dispatch on it in `apply`.  Wrap the whole
`Routes(...)` in `OpenApi.serve` and leave `apply` alone:

```scala
class Service(queryResource: QueryResource,
              newQueryResource: NewQueryResource,
              cacheStateResource: CacheStateResource,
              versionResource: VersionResource) extends HttpService {

  val routingTable = OpenApi.serve("/openapi.json", Info("Query Coordinator", "1.0"))(
    Routes(
      Route("/{String}/+", (_: Any, _: Any) => queryResource),
      Route("/{String}", (_: Any) => queryResource),
      Route("/new-query", newQueryResource),
      Route("/cache-state", cacheStateResource),
      Route("/cache-state/prime", cacheStateResource.Prime),
      Route("/version", versionResource)
    )
  )

  def apply(req: HttpRequest): HttpResponse =
    routingTable(req.requestPath) match {
      case Some(resource) => resource(req)
      case None => NotFound
    }
}
```

Here `/new-query`, `/cache-state`, `/cache-state/prime` and `/version` appear in the spec with their
methods taken from the resources.  The two catch-all `/{String}` routes do not, because their targets
take path parameters.  To include them, document them:

```scala
Route("/{String}", (_: Any) => queryResource).documented(
  Doc(get = Some(Operation("Run a query")), pathParams = List("dataset")))
```

### Alternative: `OpenApi.wrap`

`serve` adds the spec to the route tree, so it competes with your other routes (see
[route ordering](#limitations-and-notes)).  `wrap` instead sits in front of an existing
`HttpService`: requests for the spec path are answered immediately, and everything else is passed to
the wrapped service untouched.

```scala
val routes = Routes(/* ... as above, without OpenApi.serve ... */)

val service: HttpService = req =>
  routes(req.requestPath) match {
    case Some(resource) => resource(req)
    case None => NotFound
  }

val withSpec: HttpService =
  OpenApi.wrap("/openapi.json", Info("Query Coordinator", "1.0"), routes)(service)
// pass `withSpec` to your Jetty server instead of `service`
```

Because the spec path is checked before the service runs, catch-all routes like `/{String}` and
`/{String}/+` can never shadow it, whatever order routes are merged in.  The cost is that you pass the
routes twice: once to your dispatch code and once to `wrap` for generating the document.  `wrap` cannot
extract the routes from an arbitrary `HttpService`, since a service is an opaque function.

### What is derived and what you declare

| Derived automatically | Declared with `.documented(Doc(...))` |
|---|---|
| Paths | Summaries, descriptions, tags, `operationId`, `deprecated` |
| Path parameter types (`{Int}` becomes an integer, `{Long}` a 64-bit integer, `{Boolean}` a boolean, others strings) | Path parameter names |
| HTTP methods, for routes with no path parameters whose target is a `Resource` (via `allowedMethods`) | Query/header parameters |
| | Request bodies and responses |

Request bodies, query parameters and responses are not derived because handlers are plain
`HttpRequest => HttpResponse` functions and parse their inputs and build their outputs
imperatively, so there is nothing to inspect.

### Limitations and notes

* **Omitted routes.**  A route is only included if its methods can be determined: either its target
  is a `Resource` and it has no path parameters, or a `Doc` supplies the methods.  Anything else,
  such as a parameterised route with no `Doc`, a route to a plain function, or a `Directory`
  redirect, is silently left out.  (For parameterised routes the target is a function that builds
  the service from the parsed parameters, which can't be inspected without calling it.)
* **Flex matches.**  A flex route such as `/files/+` is emitted as `/files/{rest}`.  OpenAPI has no
  way to express a multi-segment parameter, so this is an approximation.
* **No schema registry.**  `Schema.ref("Widget")` produces a `$ref` to `#/components/schemas/Widget`,
  but the generated document has no `components` section.  Define the schema yourself or use inline
  schemas (`Schema.obj`, `Schema.array`, `Schema.raw`) instead.
* **Format.**  Output is OpenAPI 3.0.3 JSON only; there is no YAML.
* **Hand-attached docs can drift** from the actual handler behaviour, since they are declared separately.
* **Routes must be built with the `Route` macros** (or `PathTreeBuilder`).  Hand-built `PathTree`s
  carry no path pattern and are ignored by the generator.
* **Docs apply to the whole tree they are attached to.**  `.documented(doc)` on a merged `Routes(...)`
  tree attaches `doc` to every route in it, so attach it to a single `Route(...)`.
* **Spec path.**  It must be a literal path (no `{...}` parameters or flex markers) and non-empty.
* **Route ordering (`serve` only).**  `serve` merges the spec route into the tree last, so it beats
  catch-alls such as `/{String}` and `/{String}/+` in the tree it is given.  The router resolves
  same-depth conflicts in favour of the route registered last, though, so a catch-all merged *after*
  `serve(...)` will shadow a single-segment spec path like `/openapi.json`.  Call `serve` on the
  complete tree and merge nothing after it, use a multi-segment spec path (e.g. `/docs/openapi.json`),
  or use `wrap`, which has no ordering dependence.
