package com.socrata.http.internal.filter

import javax.servlet.http.HttpServletRequest

import com.socrata.http.server.{HttpService, HttpResponse, SimpleFilter}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.implicits._



class ThrottlingFilter() extends SimpleFilter[HttpServletRequest, HttpResponse] {
  override def apply(req:HttpServletRequest, service:HttpService) = {
    val token = req.header("X-App-token")
    token match  {
      case Some(tok) => ???

      case None => Forbidden ~> ContentType("application/json") ~> Content("""{ "message":"Please specify an app token"}""")
    }
  }

}
