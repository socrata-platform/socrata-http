package com.socrata.http.server.routing

case class OptionallyTypedPathComponent[T](value: T, extension: Option[String])

case class TypedPathComponent[T](value: T, extension: String)
