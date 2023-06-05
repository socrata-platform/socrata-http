package com.socrata.http.server.ext

import com.socrata.http.server.HttpResponse

sealed trait HandlerDecision[+T]
case class Accepted[+T](response: T) extends HandlerDecision[T]
case class Rejected(response: HttpResponse) extends HandlerDecision[Nothing]
