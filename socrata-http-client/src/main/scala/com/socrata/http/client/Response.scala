package com.socrata.http.client

import java.io.{InputStreamReader, Reader, InputStream}
import javax.activation.{MimeTypeParseException, MimeType}
import java.nio.charset.{UnsupportedCharsetException, IllegalCharsetNameException, StandardCharsets, Charset}

import com.rojoma.json.io.{JsonReader, FusedBlockJsonEventIterator, JsonEvent}
import com.rojoma.json.ast.JValue
import com.rojoma.json.codec.JsonCodec
import com.rojoma.json.util.JsonArrayIterator

import com.socrata.http.client.exceptions._
import com.socrata.http.common.util.{AcknowledgeableInputStream, Acknowledgeable}

/**
 * An HTTP response object.
 *
 * While reading the response body, timeouts may be thrown if there was a receive timeout or a
 * full-lifecycle timeout on the request.  If a ping target was provided, there may be a
 * liveness-check exeption.
 *
 * @note The response body may only be reified into an object once; if it is done more
 *       than once, an [[java.lang.IllegalStateException]] will be thrown.
 */
trait Response extends ResponseInfo {
  /**
   * Detects whether the response contains JSON data.
   *
   * @return whether or not the headers claim the content to be JSON.
   * @throws com.socrata.internal.http.exceptions.UnparsableContentType if the `Content-Type` header does not contain
   *                                                                    a valid MIME type.
   * @throws com.socrata.internal.http.exceptions.MultipleContentTypesInResponse if there is more than one `Content-Type`
   *                                                                             header in the response.
   */
  def isJson: Boolean

  /**
   * Detects the character encoding of the response body.
   *
   * @return the `Charset` named by the "`charset`" parameter of the `Content-Type`, or ISO-8859-1
   *         if there is none.
   * @throws com.socrata.internal.http.exceptions.UnparsableContentType if the `Content-Type` header does not contain
   *                                                                    a valid MIME type.
   * @throws com.socrata.internal.http.exceptions.MultipleContentTypesInResponse if there is more than one `Content-Type`
   *                                                                             header in the response.
   * @throws com.socrata.internal.http.exceptions.IllegalCharsetName if the parameter exists but cannot
   *                                                                 name any hypothetical charset.
   * @throws com.socrata.internal.http.exceptions.UnsupportedCharset if the parameter names a charset
   *                                                                 unknown to the JVM.
   */
  def charset: Charset

  /** `true` if the stream has been created. */
  def streamCreated: Boolean

  /** Gets the response body as an `InputStream`.  The returned input stream will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    */
  def asInputStream(maximumSizeBetweenAcks: Long = Long.MaxValue): InputStream with Acknowledgeable

  /** Gets the response body as a `Reader`.  The returned reader will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.internal.http.exceptions.UnparsableContentType if the `Content-Type` header does not contain
    *                                                                    a valid MIME type.
    * @throws com.socrata.internal.http.exceptions.MultipleContentTypesInResponse if there is more than one `Content-Type`
    *                                                                             header in the response.
    * @throws com.socrata.internal.http.exceptions.IllegalCharsetName if the `Content-Type` header contains an invalid `charset`
    *                                                                 parameter.
    * @throws com.socrata.internal.http.exceptions.UnsupportedCharset if the `Content-Type` header contains a valid but
    *                                                                 unknown `charset` parameter.
    */
  def asReader(maximumSizeBetweenAcks: Long = Long.MaxValue): Reader with Acknowledgeable

  /** Gets the response body as an `Iterator[JsonEvent]`.  The returned iterator will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.  It may also throw a `JsonReaderException` if the body is not well-formed
    * JSON.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.internal.http.exceptions.ContentTypeException if the response does not have an interpretable `Content-type`
    *                                                                   or if it is not application/json.
    */
  def asJsonEvents(maximumSizeBetweenAcks: Long = Long.MaxValue): Iterator[JsonEvent] with Acknowledgeable

  /** Gets the response body as a `JValue`.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.internal.http.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.internal.http.exceptions.ContentTypeException if the response does not have an interpretable `Content-type`
    *                                                                   or if it is not application/json.
    * @throws com.rojoma.json.io.JsonReaderException if the response is ill-formed.
    */
  def asJValue(approximateMaximumSize: Long = Long.MaxValue): JValue

  /** Gets the response body as an instance of a class which is convertable from JSON.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.internal.http.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.internal.http.exceptions.ContentTypeException if the response does not have an interpretable `Content-type` or if
    *                                                                   it is not application/json.
    * @throws com.rojoma.json.io.JsonReaderException if the response is ill-formed.
    */
  def asValue[T : JsonCodec](approximateMaximumSize: Long = Long.MaxValue): Option[T]

  /** Gets the response body as an instance of a class which is convertable from JSON.
    *
    * The retured iterator may throw JsonArrayIterator.ElementDecodeException if one if its elements
    * cannot be decoded to `T`.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.internal.http.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.internal.http.exceptions.ContentTypeException if the response does not have an interpretable `Content-type` or if
    *                                                                   it is not application/json.
    * @throws com.rojoma.json.io.JsonReaderException if the response is ill-formed or it is not an array.
    */
  def asArray[T : JsonCodec](approximateMaximumElementSize: Long = Long.MaxValue): Iterator[T]
}

class StandardResponse(responseInfo: ResponseInfo, rawInputStream: InputStream) extends Response {
  private[this] var _streamCreated = false
  def streamCreated = _streamCreated

  private[this] lazy val contentType = responseInfo.headers("content-type") match {
    case Array(ct) =>
      try {
        Some(new MimeType(ct))
      } catch {
        case _: MimeTypeParseException =>
          unparsableContentType(ct)
      }
    case Array() =>
      None
    case _ =>
      multipleContentTypesInResponse()
  }

  lazy val charset = contentType match {
    case Some(ct) =>
      try {
        Option(ct.getParameter("charset")).map(Charset.forName).getOrElse(StandardCharsets.ISO_8859_1)
      } catch {
        case e: IllegalCharsetNameException =>
          illegalCharsetName(e.getCharsetName)
        case e: UnsupportedCharsetException =>
          unsupportedCharset(e.getCharsetName)
      }
    case None =>
      StandardCharsets.ISO_8859_1
  }

  def asInputStream(maximumSizeBetweenAcks: Long): InputStream with Acknowledgeable = {
    if(_streamCreated) throw new IllegalStateException("Already got the response body")
    _streamCreated = true
    new AcknowledgeableInputStream(rawInputStream, maximumSizeBetweenAcks)
  }

  def asReader(maximumSizeBetweenAcks: Long) = {
    val stream = asInputStream(maximumSizeBetweenAcks)
    new InputStreamReader(stream, charset) with Acknowledgeable {
      def acknowledge() = stream.acknowledge()
    }
  }

  lazy val isJson: Boolean =
    contentType match {
      case Some(ct) => ct.getBaseType == HttpClient.jsonContentTypeBase
      case None => false
    }

  def asJsonEvents(maximumSizeBetweenAcks: Long): Iterator[JsonEvent] with Acknowledgeable = {
    if(!isJson) throw responseNotJson(contentType)
    val reader = asReader(maximumSizeBetweenAcks)
    new FusedBlockJsonEventIterator(reader) with Acknowledgeable {
      def acknowledge() = reader.acknowledge()
    }
  }

  def asJValue(approximateMaximumSize: Long): JValue =
    JsonReader.fromEvents(asJsonEvents(approximateMaximumSize))

  def asValue[T: JsonCodec](approximateMaximumSize: Long): Option[T] =
    JsonCodec[T].decode(asJValue(approximateMaximumSize))

  def asArray[T: JsonCodec](approximateMaximumElementSize: Long): Iterator[T] =
    new Iterator[T] {
      val events = asJsonEvents(approximateMaximumElementSize)
      val rawIt = JsonArrayIterator[T](events)

      def hasNext = {
        events.acknowledge()
        rawIt.hasNext
      }

      def next() = {
        events.acknowledge()
        rawIt.next()
      }
    }

  def resultCode: Int = responseInfo.resultCode
  def headers(name: String): Array[String] = responseInfo.headers(name)
  def headerNames: Set[String] = responseInfo.headerNames
}
