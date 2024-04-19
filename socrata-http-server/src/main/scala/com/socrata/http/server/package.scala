package com.socrata.http

import jakarta.servlet.http.HttpServletResponse

import com.socrata.http.server.HttpRequest

package object server {
  type Service[-A, +B] = (A => B)
  type SimpleFilter[A, B] = Filter[A, B, A, B]
  type HttpResponse = HttpServletResponse => Unit
  type HttpService = Service[HttpRequest, HttpResponse]
}
