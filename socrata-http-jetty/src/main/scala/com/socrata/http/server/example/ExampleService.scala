package com.socrata.http.server.example

import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.SimpleResource
import com.socrata.http.server.routing.SimpleRouteContext._
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}


/**
 * Every socrata-http app needs a Router.  This simple example doesn't
 * take parameters, but you could easily define ones that do.  The type
 * to pass to Route changes to A => HttpService.
 */
class ExampleRouter(exampleResource: HttpService) {
  val router = Routes(
    Route("/v1/widgets", exampleResource)
  )

  def route(req: HttpRequest): HttpResponse = {
    import com.socrata.http.server.HttpRequest.HttpRequestApi
    val reqApi = new HttpRequestApi(req)

    router(reqApi.requestPath) match {
      case Some(s) =>
        s(req)
      case None =>
       BadRequest ~> Content("text/plain", "Bad Request")
    }
  }
}

/**
 * An example service which does nothing but sleep.  Intended for testing
 * load and queues.
 */
class ExampleService(sleepTimeMs: Int) {
  import com.socrata.http.server.responses._

  case object exampleRoutes extends SimpleResource {
    override def get = { req =>
      println("Before sleeping...")
      Thread sleep sleepTimeMs
      println("Ok!")
      OK ~> Content("text/plain", "Some funk")
    }
  }
}