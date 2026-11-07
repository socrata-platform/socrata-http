package com.socrata.http.server.openapi

import org.scalatest.{FunSuite, MustMatchers}
import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.interpolation._

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import jakarta.servlet.http.HttpServletRequest
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.server.{ConcreteHttpRequest, HttpRequest, HttpService}
import com.socrata.http.server.routing.SimpleResource
import com.socrata.http.server.routing.SimpleRouteContext._
import com.socrata.http.server.openapi.OpenApi.RouteDocs

class OpenApiTest extends FunSuite with MustMatchers {
  object widgets extends SimpleResource {
    override def get = _ => null
    override def post = _ => null
  }
  val widget: Int => HttpService = _ => null
  val apiInfo = Info("Test", "1.0")

  private def paths(routes: com.socrata.http.server.routing.PathTree[_]) =
    OpenApi.generate(routes, apiInfo).fields("paths").asInstanceOf[JObject]

  test("methods of a parameterless Resource are derived, and results are sorted by path") {
    val p = paths(Routes(Route("/v1/widgets", widgets), Route("/a", widgets)))
    p.fields.keys.toList.sorted must equal (List("/a", "/v1/widgets"))
    p.fields("/v1/widgets").asInstanceOf[JObject].fields.keySet must equal (Set("get", "post"))
  }

  test("path params are converted and typed") {
    val d: Doc = Doc(get = Some(Operation("get a widget")), pathParams = List("id"))
    val p = paths(Route("/v1/widgets/{Int}", widget).documented(d))
    val op = p.fields("/v1/widgets/{id}").asInstanceOf[JObject].fields("get").asInstanceOf[JObject]
    op.fields("summary") must equal (JString("get a widget"))
    op.fields("parameters") must equal (json"""[{name:"id", in:"path", required:true, schema:{type:"integer", format:"int32"}}]""")
    op.fields("responses") must equal (json"""{"200": {description: "OK"}}""")
  }

  test("undocumented routes with unknown methods are omitted") {
    paths(Route("/v1/widgets/{Int}", widget)).fields must be (Symbol("empty"))
  }

  test("query params, request body and responses are rendered") {
    val d: Doc = Doc(post = Some(Operation(
      parameters = List(Parameter("limit", schema = Schema.integer)),
      requestBody = Some(RequestBody(schema = Schema.ref("Widget"))),
      responses = Map(201 -> Response.json("created", Schema.ref("Widget")), 400 -> Response("bad")))))
    val p = paths(Route("/v1/widgets", widgets).documented(d))
    val post = p.fields("/v1/widgets").asInstanceOf[JObject].fields("post").asInstanceOf[JObject]
    post.fields("parameters") must equal (json"""[{name:"limit", in:"query", required:false, schema:{type:"integer", format:"int32"}}]""")
    post.fields("requestBody") must equal (json"""{required:true, content:{"application/json":{schema:{"$$ref":"#/components/schemas/Widget"}}}}""")
    post.fields("responses").asInstanceOf[JObject].fields.keySet must equal (Set("201", "400"))
  }

  test("serve adds a route at a configurable path, and it excludes itself from the spec") {
    val routes = OpenApi.serve("/docs/api.json", apiInfo)(Route("/v1/widgets", widgets))
    routes(List("docs", "api.json")) must be ('defined)
    routes(List("v1", "widgets")) must be ('defined)
    routes(List("openapi.json")) must be (None)
  }

  test("spec has openapi version, info and servers") {
    val spec = OpenApi.generate(Route("/v1/widgets", widgets), apiInfo, Seq(Server("https://example.com")))
    spec.fields("openapi") must equal (JString("3.0.3"))
    spec.fields("servers") must equal (json"""[{url:"https://example.com"}]""")
  }

  private val oneSvc: HttpService = _ => _ => ()
  private val flexSvc: HttpService = _ => _ => ()
  private val catchAlls =
    Routes(Route("/{String}", (_: String) => oneSvc), Route("/{String}/+", (_: String, _: List[String]) => flexSvc))

  test("serve: the spec route wins over catch-all /{String} and /{String}/+ routes in the tree it is given") {
    for(specPath <- List("/openapi.json", "/docs/openapi.json")) {
      val comps = specPath.split("/").filter(_.nonEmpty).toList
      val t = OpenApi.serve(specPath, apiInfo)(catchAlls)
      val svc = t(comps).get
      withClue(specPath) {
        (svc eq oneSvc) must be (false)
        (svc eq flexSvc) must be (false)
      }
      // and the catch-alls still work for everything else
      (t(List("other")).get eq oneSvc) must be (true)
      (t(List("other", "stuff", "here")).get eq flexSvc) must be (true)
    }
  }

  test("order matters: a catch-all merged AFTER serve(...) shadows a single-segment spec path") {
    val t = OpenApi.serve("/openapi.json", apiInfo)(Routes(Route("/v1/widgets", widgets))).merge(catchAlls)
    (t(List("openapi.json")).get eq oneSvc) must be (true)
  }

  private def requestFor(uri: String): HttpRequest = {
    val servletReq = Proxy.newProxyInstance(getClass.getClassLoader, Array(classOf[HttpServletRequest]), new InvocationHandler {
      def invoke(proxy: Object, m: Method, args: Array[Object]): Object =
        if(m.getName == "getRequestURI") uri else null
    }).asInstanceOf[HttpServletRequest]
    new ConcreteHttpRequest(new HttpRequest.AugmentedHttpServletRequest(servletReq), None, new ResourceScope)
  }

  test("wrap: the spec path is answered before the wrapped service, so catch-alls cannot shadow it") {
    var called = List.empty[String]
    val underlying: HttpService = { req =>
      called ::= req.servletRequest.getRequestURI
      _ => ()
    }
    val svc = OpenApi.wrap("/openapi.json", apiInfo, catchAlls)(underlying)

    svc(requestFor("/openapi.json"))
    called must equal (Nil)

    svc(requestFor("/other"))
    svc(requestFor("/other/stuff"))
    called.reverse must equal (List("/other", "/other/stuff"))
  }

  test("wrap and serve reject spec paths that are not literal") {
    an [IllegalArgumentException] must be thrownBy OpenApi.wrap("/{String}", apiInfo, catchAlls)(_ => _ => ())
    an [IllegalArgumentException] must be thrownBy OpenApi.serve("/docs/+", apiInfo)(catchAlls)
  }
}
