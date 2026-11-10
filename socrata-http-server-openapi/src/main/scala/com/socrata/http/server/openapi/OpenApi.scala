package com.socrata.http.server.openapi

import com.rojoma.json.v3.ast._

import com.socrata.http.server.{HttpRequest, HttpService}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{PathTree, Resource, RouteFunction}

/** Generates an OpenAPI 3.0 document from a route tree.
  *
  * Everything is derived from the routes themselves where possible: paths and path-parameter
  * types come from the path spec and, for routes without path parameters whose target is a
  * `Resource`, the HTTP methods come from `Resource#allowedMethods`.  Query parameters, bodies and
  * responses cannot be derived, so they come from a `Doc` attached with [[OpenApi.RouteDocs#documented]].
  * Routes that have neither a `Resource` target nor a `Doc` cannot have their methods
  * determined and are omitted.
  */
object OpenApi {
  private val methodOrder = List("get", "put", "post", "delete", "options", "head", "patch", "trace")

  /** Adds `.documented(doc)` to route trees. */
  implicit class RouteDocs[R](private val tree: PathTree[R]) extends AnyVal {
    def documented(doc: Doc): PathTree[R] = tree.withAttribute(doc)
  }

  /** Builds the OpenAPI document for every documentable route in `routes`. */
  def generate(routes: PathTree[_], info: Info, servers: Seq[Server] = Nil): JObject = {
    val byPath = routes.leaves.toList.collect { case rf: RouteFunction[_] => rf }
      .flatMap(describe)
      .groupBy(_._1)
      .map { case (path, entries) =>
        path -> JObject(entries.flatMap(_._2).toMap)
      }

    val base = Map[String, JValue](
      "openapi" -> JString("3.0.3"),
      "info" -> JObject(
        Map[String, JValue]("title" -> JString(info.title), "version" -> JString(info.version)) ++
          info.description.map(d => "description" -> JString(d))),
      "paths" -> JObject(byPath.toSeq.sortBy(_._1).toMap))

    JObject(
      if(servers.isEmpty) base
      else base + ("servers" -> JArray(servers.map { s =>
        JObject(Map[String, JValue]("url" -> JString(s.url)) ++ s.description.map(d => "description" -> JString(d)))
      })))
  }

  /** Renders the same document as [[generate]], as YAML. */
  def generateYaml(routes: PathTree[_], info: Info, servers: Seq[Server] = Nil): String =
    Yaml.render(generate(routes, info, servers))

  // The (literal path components -> service) pairs for the JSON endpoint and, if requested, the YAML endpoint.
  private def specEndpoints(specPath: String, yamlPath: Option[String], info: Info, servers: Seq[Server], routes: PathTree[_]): List[(List[String], HttpService)] = {
    val jsonComponents = literalComponents(specPath)
    val yamlComponents = yamlPath.map(literalComponents)
    require(!yamlComponents.contains(jsonComponents), "The YAML spec path must differ from the JSON spec path")

    lazy val spec = generate(routes, info, servers)
    lazy val yaml = Yaml.render(spec)
    val jsonService: HttpService = { (req: HttpRequest) => OK ~> Json(spec) }
    val yamlService: HttpService = { (req: HttpRequest) => OK ~> Content("application/yaml; charset=UTF-8", yaml) }

    (jsonComponents -> jsonService) :: yamlComponents.map(_ -> yamlService).toList
  }

  /** Returns `routes` merged with a route serving its own OpenAPI document as JSON at `specPath`
    * (e.g. `"/openapi.json"`) and, if `yamlPath` is given, as YAML at `yamlPath`
    * (e.g. `Some("/openapi.yaml")`).  The paths are plain runtime strings, so they can come from
    * config.  The served document describes `routes` only, not the spec endpoints themselves.  To
    * leave the spec out, simply don't call this.
    */
  def serve[U](specPath: String, info: Info, servers: Seq[Server] = Nil, yamlPath: Option[String] = None)(routes: PathTree[List[Any] => HttpService]): PathTree[List[Any] => HttpService] =
    specEndpoints(specPath, yamlPath, info, servers, routes).foldLeft(routes) { case (tree, (components, service)) =>
      tree.merge(PathTree[List[Any] => HttpService, String](components, (_: List[Any]) => service, false))
    }

  /** Wraps `service` so that requests for `specPath` (and `yamlPath`, if given) are answered with the
    * OpenAPI document for `routes`, and every other request is passed to `service` untouched.  The
    * spec paths are checked before `service` runs, so no route in `service` can shadow them regardless
    * of merge order.  `routes` is only used to generate the document; dispatching is still up to `service`.
    */
  def wrap(specPath: String, info: Info, routes: PathTree[_], servers: Seq[Server] = Nil, yamlPath: Option[String] = None)(service: HttpService): HttpService = {
    val endpoints = specEndpoints(specPath, yamlPath, info, servers, routes).toMap

    { (req: HttpRequest) =>
      endpoints.get(req.requestPath) match {
        case Some(spec) => spec(req)
        case None => service(req)
      }
    }
  }

  private def literalComponents(specPath: String): List[String] = {
    val components = specPath.split('/').filter(_.nonEmpty).toList
    require(components.nonEmpty, "The OpenAPI spec path must not be empty")
    require(components.forall(c => !c.contains('{') && !c.endsWith("+") && !c.endsWith("*")), "The OpenAPI spec path must be a literal path")
    components
  }

  // Returns (openapi path template, method -> operation json) for one route.
  private def describe(rf: RouteFunction[_]): Option[(String, Map[String, JValue])] = {
    val docs = rf.attributes.collect { case d: Doc => d }
    val documented = docs.flatMap(_.operations).toMap
    val (template, pathParams) = convertPath(rf.pathSpec, docs.flatMap(_.pathParams).toList)

    val derivedMethods: Set[String] = rf.target match {
      case r: Resource[_, _] if pathParams.isEmpty =>
        r.allowedMethods.map(_.toLowerCase(java.util.Locale.US))
      case _ => Set.empty
    }

    val methods = (derivedMethods ++ documented.keySet).filter(methodOrder.contains)
    if(methods.isEmpty) None
    else {
      val ops = methodOrder.filter(methods).map { m =>
        m -> operationJson(documented.getOrElse(m, Operation()), pathParams)
      }
      Some(template -> ops.toMap)
    }
  }

  private case class PathParam(name: String, schema: Schema, description: Option[String])

  private def typeSchema(typeName: String): Schema = typeName.split('.').last match {
    case "Int" | "Integer" | "Short" | "Byte" => Schema.integer
    case "Long" => Schema.long
    case "Boolean" => Schema.boolean
    case "BigDecimal" | "Double" | "Float" => Schema.number
    case _ => Schema.string
  }

  // "/v1/widgets/{Int}/parts/{{Foo:}}/+" -> ("/v1/widgets/{param1}/parts/{param2}/{rest}", [params])
  private def convertPath(pathSpec: String, names: List[String]): (String, List[PathParam]) = {
    val params = List.newBuilder[PathParam]
    var n = 0
    var remainingNames = names
    def nextName(default: String) = remainingNames match {
      case h :: t => remainingNames = t; h
      case Nil => default
    }
    val parts = pathSpec.split("/", -1).drop(1).map {
      case "?" =>
        n += 1; val name = nextName("param" + n)
        params += PathParam(name, Schema.string, None); "{" + name + "}"
      case "+" | "*" =>
        val name = nextName("rest")
        params += PathParam(name, Schema.string, Some("Remainder of the path")); "{" + name + "}"
      case c if c.startsWith("{{") =>
        n += 1; val name = nextName("param" + n)
        val typeName = c.stripPrefix("{{").stripSuffix("}}").takeWhile(ch => ch != ':' && ch != '!')
        params += PathParam(name, typeSchema(typeName), None); "{" + name + "}"
      case c if c.startsWith("{") =>
        n += 1; val name = nextName("param" + n)
        params += PathParam(name, typeSchema(c.stripPrefix("{").stripSuffix("}")), None); "{" + name + "}"
      case literal => literal
    }
    (parts.mkString("/", "/", ""), params.result())
  }

  private def operationJson(op: Operation, pathParams: List[PathParam]): JValue = {
    def opt(k: String, v: Option[String]) = v.map(k -> JString(_))

    val pathParamJson = pathParams.map { p =>
      JObject(Map[String, JValue](
        "name" -> JString(p.name), "in" -> JString("path"), "required" -> JBoolean.canonicalTrue, "schema" -> p.schema.json) ++
        opt("description", p.description))
    }
    val otherParamJson = op.parameters.map { p =>
      JObject(Map[String, JValue](
        "name" -> JString(p.name), "in" -> JString(p.in.name), "required" -> JBoolean(p.required), "schema" -> p.schema.json) ++
        opt("description", p.description))
    }
    val allParams = pathParamJson ++ otherParamJson

    val responses: Map[Int, Response] =
      if(op.responses.isEmpty) Map(200 -> Response("OK")) else op.responses

    val fields = Map[String, JValue](
      "responses" -> JObject(responses.toSeq.sortBy(_._1).map { case (code, r) =>
        code.toString -> JObject(Map[String, JValue]("description" -> JString(r.description)) ++
          (if(r.content.isEmpty) None
           else Some("content" -> JObject(r.content.map { case (ct, s) => ct -> JObject(Map("schema" -> s.json)) }))))
      }.toMap)) ++
      opt("summary", op.summary) ++ opt("description", op.description) ++ opt("operationId", op.operationId) ++
      (if(op.tags.isEmpty) None else Some("tags" -> JArray(op.tags.map(JString(_))))) ++
      (if(allParams.isEmpty) None else Some("parameters" -> JArray(allParams))) ++
      op.requestBody.map { b =>
        "requestBody" -> JObject(Map[String, JValue](
          "required" -> JBoolean(b.required),
          "content" -> JObject(Map(b.contentType -> JObject(Map("schema" -> b.schema.json))))) ++
          opt("description", b.description))
      } ++
      (if(op.deprecated) Some("deprecated" -> JBoolean.canonicalTrue) else None)

    JObject(fields)
  }
}
