package com.socrata.http.client

import javax.activation.MimeType

package object exceptions {
  def connectTimeout() = throw new ConnectTimeout
  def receiveTimeout(cause: Throwable = null) = throw new ReceiveTimeout(cause)
  def connectFailed(cause: java.net.ConnectException) = throw new ConnectFailed(cause)
  def livenessCheckFailed() = throw new LivenessCheckFailed
  def fullTimeout() = throw new FullTimeout
  def multipleContentTypesInResponse() = throw new MultipleContentTypesInResponse
  def unparsableContentType(contentType: String) = throw new UnparsableContentType(contentType)
  def responseNotJson(mimeType: Option[MimeType]) = throw new UnexpectedContentType(got = mimeType, expected = HttpClient.jsonContentTypeBase)
  def illegalCharsetName(charsetName: String) = throw new IllegalCharsetName(charsetName)
  def unsupportedCharset(charsetName: String) = throw new UnsupportedCharset(charsetName)
  def noBodyInResponse() = throw new NoBodyInResponse
}
