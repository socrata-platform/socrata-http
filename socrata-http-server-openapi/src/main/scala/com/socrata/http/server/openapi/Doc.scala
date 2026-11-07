package com.socrata.http.server.openapi

sealed abstract class ParameterLocation(val name: String)
object ParameterLocation {
  case object Query extends ParameterLocation("query")
  case object Header extends ParameterLocation("header")
}

final case class Parameter(name: String,
                           in: ParameterLocation = ParameterLocation.Query,
                           schema: Schema = Schema.string,
                           required: Boolean = false,
                           description: Option[String] = None)

final case class RequestBody(contentType: String = "application/json",
                             schema: Schema = Schema.anyObject,
                             required: Boolean = true,
                             description: Option[String] = None)

final case class Response(description: String,
                          content: Map[String, Schema] = Map.empty)

object Response {
  def json(description: String, schema: Schema): Response =
    Response(description, Map("application/json" -> schema))
}

/** Documentation for one HTTP method on one route. */
final case class Operation(summary: Option[String] = None,
                           description: Option[String] = None,
                           operationId: Option[String] = None,
                           tags: List[String] = Nil,
                           parameters: List[Parameter] = Nil,
                           requestBody: Option[RequestBody] = None,
                           responses: Map[Int, Response] = Map.empty,
                           deprecated: Boolean = false)

object Operation {
  def apply(summary: String): Operation = Operation(summary = Some(summary))
}

/** Opt-in documentation for a route, attached with `.documented(...)`.
  *
  * @param operations lower-case HTTP method -> operation
  * @param pathParams names for the route's path parameters, in order of appearance
  *                   (defaults to `param1`, `param2`, ... and `rest` for a flex match)
  */
final case class Doc(operations: Map[String, Operation],
                     pathParams: List[String])

object Doc {
  def apply(get: Option[Operation] = None,
            put: Option[Operation] = None,
            post: Option[Operation] = None,
            delete: Option[Operation] = None,
            patch: Option[Operation] = None,
            head: Option[Operation] = None,
            options: Option[Operation] = None,
            pathParams: List[String] = Nil): Doc = {
    val ops = Seq("get" -> get, "put" -> put, "post" -> post, "delete" -> delete,
                  "patch" -> patch, "head" -> head, "options" -> options)
      .collect { case (m, Some(op)) => m -> op }.toMap
    Doc(ops, pathParams)
  }
}

final case class Info(title: String, version: String, description: Option[String] = None)

final case class Server(url: String, description: Option[String] = None)
