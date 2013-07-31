package com.socrata.http.client

trait ResponseInfo {
  def resultCode: Int
  def headers(name: String): Array[String] // this will return an empty array if the header does not exist
  def headerNames: Set[String] // All the header names, canonicalized to lower-case
}
