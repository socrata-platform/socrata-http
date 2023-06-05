package com.socrata.http.server.ext

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.http.server.HttpRequest
import com.socrata.http.server.util.Precondition

trait RequestParts {
  def resourceScope: ResourceScope
  def method: String
  def headers: HeaderMap
  def uri: String
  def query: Option[String]
  def extensions: Extensions
  def requestId: RequestId
  def precondition: Precondition

  def withExtension[T](kv: (Extensions.Key[T], T)): RequestParts
}

object RequestParts {
  private class RequestPartsImpl(req: HttpRequest, headersBox: LazyBox[HeaderMap], val extensions: Extensions) extends RequestParts {
    def resourceScope = req.resourceScope

    def method = req.method
    def uri = req.requestPathStr
    def query = req.queryStr
    def requestId = new RequestId(req.requestId)
    def precondition = req.precondition

    def headers = headersBox.get

    def withExtension[T](kv: (Extensions.Key[T], T)) =
      new RequestPartsImpl(req, headersBox, extensions + kv)
  }

  def from(req: HttpRequest): RequestParts =
    new RequestPartsImpl(
      req,
      new LazyBox(req.headerNames.foldLeft(new HeaderMap) { (acc, name) =>
                    req.headers(name).foldLeft(acc) { (acc, value) =>
                      acc.appendHeader(HeaderName(name), HeaderValue(value))
                    }
                  }),
      Extensions.empty
    )
}
