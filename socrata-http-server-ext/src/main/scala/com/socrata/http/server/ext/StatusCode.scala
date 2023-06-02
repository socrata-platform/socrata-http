package com.socrata.http.server.ext

case class StatusCode(code: Int)

object StatusCode {
  val OK = StatusCode(200)
  val NotFound = StatusCode(404)
}
