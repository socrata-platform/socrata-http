package com.socrata.http.client.exceptions

import javax.activation.MimeType
import java.net.ConnectException

class HttpClientException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class HttpClientTimeoutException extends HttpClientException
class ConnectTimeout extends HttpClientTimeoutException
class ReceiveTimeout extends HttpClientTimeoutException
class FullTimeout extends HttpClientTimeoutException

class LivenessCheckFailed extends HttpClientException
class ConnectFailed(val cause: ConnectException) extends HttpClientException(cause = cause) // failed for not-timeout reasons
class NoBodyInResponse extends HttpClientException

class ContentTypeException(msg: String = null) extends HttpClientException(msg)
class MultipleContentTypesInResponse extends ContentTypeException
class UnparsableContentType(val contentType: String) extends ContentTypeException("Unable to parse content-type: " + contentType)
class UnexpectedContentType(val got: Option[MimeType], val expected: String) extends ContentTypeException("Wanted content-type " + expected + "; got " + got.map(_.getBaseType).getOrElse("nothing"))
class IllegalCharsetName(val charsetName: String) extends ContentTypeException("Illegal charset name " + charsetName)
class UnsupportedCharset(val charsetName: String) extends ContentTypeException("Unsupported charset " + charsetName)
