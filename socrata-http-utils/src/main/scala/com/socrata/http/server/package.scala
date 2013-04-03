package com.socrata.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

package object server {
  type Service[-A, +B] = (A => B)
  type SimpleFilter[A, B] = Filter[A, B, A, B]
  type HttpResponse = HttpServletResponse => Unit
  type HttpService = Service[HttpServletRequest, HttpResponse]
}
