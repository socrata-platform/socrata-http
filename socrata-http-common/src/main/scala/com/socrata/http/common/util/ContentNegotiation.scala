package com.socrata.http.common.util

import jakarta.activation.MimeType
import java.nio.charset.{StandardCharsets, UnsupportedCharsetException, IllegalCharsetNameException, Charset}
import com.socrata.http.common.util.HttpUtils.{LanguageRange, CharsetRange, MediaRange}
import java.awt.datatransfer.MimeTypeParseException
import scala.collection.mutable

case class AliasedCharset(charset: Charset, alias: String)

class ContentNegotiation(mimeTypes: Iterable[(MimeType, Option[String])], languages: Iterable[String]) {
  private val mimetypeSet = locally {
    val s = new mutable.LinkedHashSet[MimeType]
    mimeTypes.foreach { mt => s += mt._1 }
    s
  }
  private val exts = mimeTypes.collect {
    case (mt, Some(v)) => v.toLowerCase -> mt
  }.toMap
  val languageTrie = languages.zipWithIndex.foldLeft(Trie.empty[String, Int]) { (acc, langIdx) =>
    val (lang, idx) = langIdx
    acc + (lang.split('-').toSeq -> idx)
  }
  val firstLanguage = languages.head.split('-').toSeq

  def apply(accept: Iterable[MediaRange], contentType: Option[String], ext: Option[String], acceptCharset: Iterable[CharsetRange], acceptLanguage: Iterable[LanguageRange]): Option[(MimeType, AliasedCharset, String)] =
    for {
      mimeType <- ContentNegotiation.mimeType(accept, contentType, ext, mimetypeSet, exts)
      charset <- ContentNegotiation.charset(acceptCharset)
    } yield (mimeType, charset, ContentNegotiation.language(acceptLanguage, languageTrie, firstLanguage))
}

object ContentNegotiation {
  // Generally speaking, for all of these we want to decorate the set of choices with
  // preference values by setting the value to the value of the most specific thing
  // in the header which matches the choice.
  //
  // Note that "matches" doesn't necessarily mean "string match", nor does it necessarily
  // mean "globby match".  For example, an accept-language header like "en, *; q=0.5, de; q=0"
  // means "give me any English variant preferentially, then anything but German or English,
  // and NEVER German".  That is, "*" means "anything not referred to more specifically in
  // the list".

  // The set of HTTP headers that influence the negotiation decision
  val headers = Set("Accept", "Accept-Charset", "Accept-Language", "Content-Type")

  private def parseContentType(ct: String): Option[MimeType] =
    try {
      Some(new MimeType(ct))
    } catch {
      case _: MimeTypeParseException => None
    }

  def stripParams(mt: MimeType): MimeType = new MimeType(mt.getPrimaryType, mt.getSubType)

  def arrangeAccept(accept: Iterable[MediaRange]): Trie[String, Double] =
    accept.foldLeft(Trie.empty[String, Double]) { (acc, mr) =>
      if(mr.subtyp == "*") {
        if(mr.typ == "*") {
          acc + (Nil -> mr.q)
        } else {
          acc + (List(mr.typ) -> mr.q)
        }
      } else {
        acc + (List(mr.typ, mr.subtyp) -> mr.q)
      }
    }

  def qFor(xs: Iterable[String], qs: Trie[String, Double]): Double = qs.nearest(xs).getOrElse(0.0)

  private val emptyAccept = Trie[String, Double](Nil -> 1.0)

  // This currently COMPLETELY IGNORES non-q parameters on mimetypes.
  // It also assumes that the values of "exts" are a subset of the values
  // in "available", and that the iteration order of "available" reflects
  // preference.
  // We're basically ignoring the client's q-preference (except for q=0)
  // and using the request's extension and content-type to guide our choice.
  def mimeType(accept: Iterable[MediaRange], contentTypeRaw: Option[String], ext: Option[String], available: scala.collection.Set[MimeType], exts: Map[String, MimeType]): Option[MimeType] = {
    val simplifiedAccept = if(accept.isEmpty) emptyAccept else arrangeAccept(accept)
    def unacceptable(ct: MimeType): Boolean = qFor(List(ct.getPrimaryType, ct.getSubType), simplifiedAccept) <= 0
    def forContentType: Option[MimeType] = contentTypeRaw.flatMap(parseContentType).map(stripParams).filter(available).filterNot(unacceptable)
    def forExt: Option[MimeType] = ext.flatMap { (trueExt: String) =>
      exts.get(trueExt.toLowerCase)
    }.filterNot(unacceptable)
    val fromClientParams = forExt orElse forContentType
    fromClientParams.orElse {
      available.find { mt =>
        qFor(List(mt.getPrimaryType, mt.getSubType), simplifiedAccept) > 0
      }
    }
  }

  def parseCharset(cs: String): Option[Charset] =
    try {
      Some(Charset.forName(cs))
    } catch {
      case _: IllegalCharsetNameException | _: UnsupportedCharsetException => None
    }

  def arrangeAcceptCharset(accept: Iterable[CharsetRange]) =
    accept.foldLeft(Trie.empty[String, Double]) { (acc, cr) =>
      if(cr.charset == "*") acc + (Nil -> cr.q)
      else acc + (List(cr.charset) -> cr.q)
    }

  // Here we'll choose UTF-8 if it's acceptable, otherwise the charset
  // with the highest Q-value.
  private val stdCharset = Some(AliasedCharset(StandardCharsets.UTF_8, StandardCharsets.UTF_8.name))
  def charset(acceptCharset: Iterable[CharsetRange]): Option[AliasedCharset] = {
    if(acceptCharset.isEmpty) return stdCharset
    val crs = acceptCharset.toArray
    val star = acceptCharset.find(_.charset == "*")
    val othersDisallowed = star.isEmpty || star.get.q <= 0
    if(!othersDisallowed) return stdCharset
    for(r <- crs) {
      if(r.charset.equalsIgnoreCase("utf-8") && r.q > 0) return Some(AliasedCharset(StandardCharsets.UTF_8, r.charset))
    }
    java.util.Arrays.sort(crs, Ordering[Double].on[CharsetRange](- _.q))
    for(r <- crs) {
      if(r.q <= 0) return None
      parseCharset(r.charset) match {
        case Some(cs) if cs.canEncode => return Some(AliasedCharset(cs, r.charset))
        case _ => // wasn't a charset we recognize or can encode with
      }
    }
    None
  }

  def language(acceptLanguage: Iterable[LanguageRange], available: Trie[String, Int], fallback: Seq[String]): String = {
    if(acceptLanguage.isEmpty) return fallback.mkString("-")
    val simplified = acceptLanguage.toArray
    java.util.Arrays.sort(simplified, Ordering[Double].on[LanguageRange](- _.q))
    for(lr <- simplified) {
      if(lr.q <= 0) return fallback.mkString("-")
      available.subtrie(lr.language) match {
        case Some(subtrie) =>
          return (lr.language ++ subtrie.iterator.maxBy(_._2)._1).mkString("-")
        case None =>
          // ok we'll keep looking
      }
    }
    fallback.mkString("-")
  }
}
