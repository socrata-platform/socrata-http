package com.socrata.http.common.util

import java.awt.datatransfer.MimeTypeParseException
import java.nio.charset.{IllegalCharsetNameException, StandardCharsets, Charset}
import javax.activation.MimeType

import StandardCharsets._

/** Returns a `Charset` for the given mime type. */
object CharsetFor {
  sealed trait ContentTypeResult
  sealed trait ContentTypeFailure extends ContentTypeResult
  case class UnparsableContentType(contentType: String) extends ContentTypeFailure

  sealed trait MimeTypeResult extends ContentTypeResult
  sealed trait MimeTypeFailure extends MimeTypeResult with ContentTypeFailure

  case class UnknownMimeType(mimeType: MimeType) extends MimeTypeFailure
  case class IllegalCharsetName(name: String) extends MimeTypeFailure
  case class UnknownCharset(name: String) extends MimeTypeFailure
  case class Success(charset: Charset) extends MimeTypeResult

  def mimeType(mt: MimeType): MimeTypeResult = {
    Option(mt.getParameter("charset")) match {
      case Some(cs) =>
        try {
          Success(Charset.forName(cs))
        } catch {
          case e: IllegalCharsetNameException => IllegalCharsetName(cs)
          case e: UnsupportedOperationException => UnknownCharset(cs)
        }
      case None =>
        val readerOpt = for {
          subReg <- registry.get(mt.getPrimaryType)
          cs <- try { Some(subReg(mt.getSubType)) } catch { case _: NoSuchElementException => None } // fun fact: .get ignores default values
        } yield cs
        readerOpt.fold[MimeTypeResult](UnknownMimeType(mt))(Success)
    }
  }

  def contentType(ct: String): ContentTypeResult = {
    val mt = try {
      new MimeType(ct)
    } catch {
      case e: MimeTypeParseException =>
        return UnparsableContentType(ct)
    }
    mimeType(mt)
  }

  private def buildRegistry(values: (String, Charset)*): Map[String, Map[String, Charset]] = {
    val withWildcards = values.foldLeft(Map.empty[String, Map[String, Charset]]) { (acc, mimeTypeDetector) =>
      val (mimeTypeRaw, detector) = mimeTypeDetector
      val mimeType = new MimeType(mimeTypeRaw)
      val old = acc.getOrElse(mimeType.getPrimaryType, Map.empty[String, Charset])
      acc.updated(mimeType.getPrimaryType, old.updated(mimeType.getSubType, detector))
    }
    withWildcards.transform { (_, sub) =>
      sub.get("*") match {
        case Some(fallback) => (sub - "*").withDefaultValue(fallback)
        case None => sub
      }
    }
  }

  private val registry = buildRegistry(
    "application/json" -> UTF_8, // per RFC 7159; rfc4627 defines an autodetect system but it was dropped in 7159
    "text/csv" -> UTF_8, // per http://www.iana.org/assignments/media-types/text/csv
    "text/html" -> ISO_8859_1,
    "text/*" -> US_ASCII // per RFC 6657
  )
}
