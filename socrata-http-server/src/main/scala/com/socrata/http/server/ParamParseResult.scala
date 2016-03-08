package com.socrata.http.server

sealed trait ParamParseResult[+A] {
  def toOption: Option[A]
  def isValid: Boolean
  def get: A
  def getOrElse[B >: A](default: => B): B
  def map[B](f: A => B): ParamParseResult[B]
}

case class ParsedParam[+A](value: A) extends ParamParseResult[A] {
  override val toOption = Some(value)
  override val isValid = true
  override val get = value
  override def getOrElse[B >: A](default: => B) = value
  override def map[B](f: A => B) = ParsedParam(f(value))
}

case class UnparsableParam(name: String, rawValue: String) extends ParamParseResult[Nothing] {
  override val toOption = None
  override val isValid = false
  override def get = throw new java.util.NoSuchElementException("Attempted to read unparsable param.")
  override def getOrElse[B](default: => B) = default
  override def map[B](f: Nothing => B) = this
}
