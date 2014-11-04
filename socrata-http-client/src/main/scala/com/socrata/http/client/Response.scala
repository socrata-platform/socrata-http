package com.socrata.http.client

import java.io.{InputStreamReader, Reader, InputStream}
import javax.activation.{MimeTypeParseException, MimeType}
import java.nio.charset.{StandardCharsets, Charset}

import com.rojoma.json.io.{JsonReader, FusedBlockJsonEventIterator, JsonEvent}
import com.rojoma.json.ast.JValue
import com.rojoma.json.codec.JsonCodec
import com.rojoma.json.util.JsonArrayIterator

import com.rojoma.json.v3.io.{JsonReader => JsonReader3, FusedBlockJsonEventIterator => FusedBlockJsonEventIterator3, JsonEvent => JsonEvent3}
import com.rojoma.json.v3.ast.{JValue => JValue3}
import com.rojoma.json.v3.codec.{DecodeError, JsonDecode}
import com.rojoma.json.v3.util.{JsonArrayIterator => JsonArrayIterator3}

import com.socrata.http.client.exceptions._
import com.socrata.http.common.util.{CharsetFor, AcknowledgeableInputStream, Acknowledgeable}

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
  import Response._

  /**
   * Detects the character encoding of the response body.
   *
   * @return the `Charset` named by the "`charset`" parameter of the `Content-Type`, or ISO-8859-1
   *         if there is none.
   * @throws com.socrata.http.client.exceptions.UnparsableContentType if the `Content-Type` header does not contain
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

  def contentType = headers("content-type") match {
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

  /** Gets the response body as an `InputStream`.  The returned input stream will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    */
  def inputStream(maximumSizeBetweenAcks: Long = Long.MaxValue): InputStream with Acknowledgeable

  /** Gets the response body as a `Reader`.  The returned reader will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.client.exceptions.UnparsableContentType if `charset` is `None` and the `Content-Type`
    *                                                                    header does not contain a valid MIME type.
    * @throws com.socrata.http.client.exceptions.MultipleContentTypesInResponse if `charset` is `None` and there is more
    *                                                                             than one `Content-Type` header in the response.
    * @throws com.socrata.http.client.exceptions.IllegalCharsetName if `charset` is `None` and the `Content-Type` header contains
    *                                                                 an invalid `charset` parameter.
    * @throws com.socrata.http.client.exceptions.UnsupportedCharset if `charset` is `None` and the `Content-Type` header contains
    *                                                                 a valid but unknown `charset` parameter.
    */
  def reader(charset: Option[Charset] = None, maximumSizeBetweenAcks: Long = Long.MaxValue): Reader with Acknowledgeable = {
    val stream = inputStream(maximumSizeBetweenAcks)
    // For now, this MUST (despite the type) return an InputStreamReader!  This is because,
    // in order to preserve binary compatibility, the StandardReader#asReader method must
    // return an InputStreamReader.  Therefore, the result of this method gets dowcast there
    // as I don't want THIS method falling into that same trap.
    new InputStreamReader(stream, charset.getOrElse(this.charset)) with Acknowledgeable {
      def acknowledge() = stream.acknowledge()
    }
  }

  /** Gets the response body as an `Iterator[JsonEvent]`.  The returned iterator will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.  It may also throw a `JsonReaderException` if the body is not well-formed
    * JSON.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type`
    *                                                                   or if it is not accepted by `acceptableContentType`
    */
  def jsonEvents(acceptableContentType: ContentP = Response.acceptJson, maximumSizeBetweenAcks: Long = Long.MaxValue): Iterator[JsonEvent] with Acknowledgeable = {
    if(!acceptableContentType(contentType)) throw responseNotJson(contentType)
    val r = reader(None, maximumSizeBetweenAcks)
    new FusedBlockJsonEventIterator(r) with Acknowledgeable {
      def acknowledge() = r.acknowledge()
    }
  }

  /** Gets the response body as an `Iterator[JsonEvent]`.  The returned iterator will
    * throw a [[com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement]] exception
    * if it receives more than `maximumSizeBetweenAcks` bytes without having its `acknowledge()`
    * method called.  It may also throw a `JsonReaderException` if the body is not well-formed
    * JSON.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type`
    *                                                                   or if it is not accepted by `acceptableContentType`
    */
  def jsonEvents3(acceptableContentType: ContentP = Response.acceptJson, maximumSizeBetweenAcks: Long = Long.MaxValue): Iterator[JsonEvent3] with Acknowledgeable = {
    if(!acceptableContentType(contentType)) throw responseNotJson(contentType)
    val r = reader(None, maximumSizeBetweenAcks)
    new FusedBlockJsonEventIterator3(r) with Acknowledgeable {
      def acknowledge() = r.acknowledge()
    }
  }

  /** Gets the response body as a `JValue`.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type`
    *                                                                   or if it is not accepted by `acceptableContentType`
    * @throws com.rojoma.json.io.JsonReaderException if the response is ill-formed.
    */
  def jValue(acceptableContentType: ContentP = Response.acceptJson, approximateMaximumSize: Long = Long.MaxValue): JValue =
    JsonReader.fromEvents(jsonEvents(acceptableContentType, approximateMaximumSize))

  /** Gets the response body as a `JValue`.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type`
    *                                                                   or if it is not accepted by `acceptableContentType`
    * @throws com.rojoma.json.v3.io.JsonReaderException if the response is ill-formed.
    */
  def jValue3(acceptableContentType: ContentP = Response.acceptJson, approximateMaximumSize: Long = Long.MaxValue): JValue3 ={
    if(!acceptableContentType(contentType)) throw responseNotJson(contentType)
    val r = reader(None, approximateMaximumSize)
    JsonReader3.fromReader(r)
  }

  /** Gets the response body as an instance of a class which is convertable from JSON.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type` or if
    *                                                                   it is not accepted by `acceptableContentType`.
    * @throws com.rojoma.json.io.JsonReaderException if the response is ill-formed.
    */
  def value[T : JsonCodec](acceptableContentType: ContentP = Response.acceptJson, approximateMaximumSize: Long = Long.MaxValue): Option[T] =
    JsonCodec[T].decode(jValue(acceptableContentType, approximateMaximumSize))

  /** Gets the response body as an instance of a class which is convertable from JSON.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type` or if
    *                                                                   it is not accepted by `acceptableContentType`.
    * @throws com.rojoma.json.v3.io.JsonReaderException if the response is ill-formed.
    */
  def value3[T : JsonDecode](acceptableContentType: ContentP = Response.acceptJson, approximateMaximumSize: Long = Long.MaxValue): Either[DecodeError, T] =
    JsonDecode[T].decode(jValue3(acceptableContentType, approximateMaximumSize))

  /** Gets the response body as an instance of a class which is convertable from JSON.
    *
    * The retured iterator may throw `JsonArrayIterator.ElementDecodeException` if one if its elements
    * cannot be decoded to `T`.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type` or if
    *                                                                   it is not accepted by `acceptableContentType`.
    * @throws com.rojoma.json.io.JsonReaderException if the response is ill-formed or it is not an array.
    */
  def array[T : JsonCodec](acceptableContentType: ContentP = Response.acceptJson, approximateMaximumElementSize: Long = Long.MaxValue): Iterator[T] =
    new AcknowledgingIterator[T, JsonEvent](jsonEvents(acceptableContentType, approximateMaximumElementSize), JsonArrayIterator[T](_))

  /** Gets the response body as an instance of a class which is convertable from JSON.
    *
    * The retured iterator may throw `JsonArrayIterator.ElementDecodeException` if one if its elements
    * cannot be decoded to `T`.
    *
    * @throws java.lang.IllegalStateException if the stream has already been created.
    * @throws com.socrata.http.common.util.TooMuchDataWithoutAcknowledgement if parsing the JValue
    *                                                                          requires reading more than `approximateMaximumSize`
    *                                                                          bytes from the response.
    * @throws com.socrata.http.client.exceptions.ContentTypeException if the response does not have an interpretable `Content-type` or if
    *                                                                   it is not accepted by `acceptableContentType`.
    * @throws com.rojoma.json.v3.io.JsonReaderException if the response is ill-formed or it is not an array.
    */
  def array3[T : JsonDecode](acceptableContentType: ContentP = Response.acceptJson, approximateMaximumElementSize: Long = Long.MaxValue): Iterator[T] =
    new AcknowledgingIterator[T, JsonEvent3](jsonEvents3(acceptableContentType, approximateMaximumElementSize), JsonArrayIterator3[T](_))
}

object Response {
  private val appJson = new MimeType("application/json")
  private val appGeoJson = new MimeType("application/vnd.geo+json")
  private val textPlain = new MimeType("text/plain")

  def matches(pattern: MimeType, candidate: MimeType): Boolean =
    pattern.getPrimaryType == candidate.getPrimaryType && (pattern.getSubType == "*" || pattern.getSubType == candidate.getSubType)

  def acceptJson(mimeType: Option[MimeType]) = mimeType.fold(false)(matches(appJson, _))
  def acceptGeoJson(mimeType: Option[MimeType]) = mimeType.fold(false) { mt =>
    matches(appJson, mt) || matches(appGeoJson, mt)
  }
  def acceptTextPlain(mimeType: Option[MimeType]) = mimeType.fold(false)(matches(textPlain, _))

  type ContentP = Option[MimeType] => Boolean

  private class AcknowledgingIterator[T, U](events: Iterator[U] with Acknowledgeable, decoder: Iterator[U] => Iterator[T]) extends Iterator[T] {
    val rawIt = decoder(events)

    def hasNext = {
      events.acknowledge()
      rawIt.hasNext
    }

    def next(): T = {
      events.acknowledge()
      rawIt.next()
    }
  }
}

class StandardResponse(responseInfo: ResponseInfo, rawInputStream: InputStream) extends Response { self =>
  private[this] var _streamCreated = false
  def streamCreated = _streamCreated

  lazy val charset = self.contentType match {
    case Some(ct) =>
      CharsetFor.mimeType(ct) match {
        case CharsetFor.Success(cs) => cs
        case CharsetFor.IllegalCharsetName(n) => illegalCharsetName(n)
        case CharsetFor.UnknownCharset(n) => unsupportedCharset(n)
        case CharsetFor.UnknownMimeType(_) => StandardCharsets.ISO_8859_1 // unhappy, but backward compat
      }
    case None =>
      StandardCharsets.ISO_8859_1
  }

  def inputStream(maximumSizeBetweenAcks: Long): InputStream with Acknowledgeable = {
    if(_streamCreated) throw new IllegalStateException("Already got the response body")
    _streamCreated = true
    new AcknowledgeableInputStream(rawInputStream, maximumSizeBetweenAcks)
  }
  def asInputStream(maximumSizeBetweenAcks: Long) = inputStream(maximumSizeBetweenAcks)

  def asReader(maximumSizeBetweenAcks: Long): InputStreamReader with Acknowledgeable =
    self.reader(maximumSizeBetweenAcks = maximumSizeBetweenAcks).asInstanceOf[InputStreamReader with Acknowledgeable]

  lazy val isJson: Boolean =
    self.contentType match {
      case Some(ct) => ct.getBaseType == HttpClient.jsonContentTypeBase
      case None => false
    }

  def asJsonEvents(maximumSizeBetweenAcks: Long): Iterator[JsonEvent] with Acknowledgeable =
    self.jsonEvents(maximumSizeBetweenAcks = maximumSizeBetweenAcks)

  def asJValue(approximateMaximumSize: Long): JValue =
    self.jValue(approximateMaximumSize = approximateMaximumSize)

  def asValue[T: JsonCodec](approximateMaximumSize: Long): Option[T] =
    self.value(approximateMaximumSize = approximateMaximumSize)

  def asArray[T: JsonCodec](approximateMaximumElementSize: Long): Iterator[T] =
    this.array[T](approximateMaximumElementSize = approximateMaximumElementSize)

  def resultCode: Int = responseInfo.resultCode
  def headers(name: String): Array[String] = responseInfo.headers(name)
  def headerNames: Set[String] = responseInfo.headerNames
}
