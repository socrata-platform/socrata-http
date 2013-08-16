package com.socrata.http.common.util

import javax.activation.MimeType
import java.nio.charset.Charset

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
  //
  // But for now, this is all TODO

  def mimeType(accept: Iterable[String], contentType: Option[String], requestURI: Option[String], available: Iterable[MimeType]): Option[MimeType] = {
    // An "Accept" header is 0 or more of comma-separated media-ranges.
    // A media-range is ("*/*" or "type/*" or "type/subtype") followed by zero or more
    // ;attribute={token|quoted-string}
    // parameters.  The parameter "q" is special; it separates mimetype-parameters
    // from accept-parameters and must be a number from 0 to 1, with at most 3 places
    // after the (optional) decimal point.
    //
    // attribute, type, and subtype are all tokens.
    //
    // If a media-range has no "q" parameter it is assumed to be 1.
    //
    // Note: some broken user-agents will send "*" in place of "*/*'.
    //
    // We will IGNORE all mimetype-parameters not set in "available".  So for example
    //    Accept: text/plain; foo=bar; q=1
    // will match an available mimetype of either "text/plain;foo=bar" or "text/plain"
    // It is only actually used to decorate the one which is the best match.
    //
    // It is not clear to me how "better match" is defined.
    //
    // We will IGNORE all accept-parameters.
    //
    // Use the request-uri, if it is set and if its extension is understood, to narrow down the list
    // of available choices before applying the algorithm.
    //
    // If, after the algorithm, there is more than one choice remaining, prefer
    // first the one that matches contentType if set, or failing that the one that
    // came first in the list of available options.
    available.headOption
  }

  def charset(acceptCharset: Iterable[String], contentType: Option[String], available: Iterable[Charset]): Option[Charset] = {
    // An "Accept-Charset" header is 1 or more of comma-separated q-decorated charsets
    // or wildcards:
    //  ("*" | charset)[;q=qvalue]
    //
    // charset is a token.  qvalue is as above in `mimeType`
    //
    // If a charset has no "q" parameter it is assumed to be 1.
    //
    // A charset matches a name if Charset.forName(charset) == Charset.forName(name)
    // (yes, ==, not eq).  There are only exact matches, not "better matches".
    //
    // If, after the algorithm, there is more than one choice remaining, prefer
    // first the one that matches contentType if set, or failing that the one that
    // came first in the list of available options.
    //
    // "available" here is slightly special.  Any charset which the JVM knows about
    // but not in the list is considered to be "available" but after all specified
    // choices.  This means that "available" should almost always be [UTF-8, ISO-8859-1]

    available.headOption
  }

  def language(acceptLanguage: Iterable[String], available: Iterable[String]): Option[String] = {
    // "Accept-Language" is much like Accept-Charset, only instead of "charset"
    // is has a language-range, which is [A-Za-z]{1,8}(-[A-Za-z]{1,8})*|\*
    // (i.e., a "*" or an unlimited number of dash-separated up-to-eight-letter blocks.)
    //
    // If a language-range has no "q" parameter it is assumed to be 1.
    //
    // A range matches a real language if it is "*" or its set of blocks is a case-insensitive prefix
    // of the set of blocks of the name of the actual language.  "Better match" means "longer prefix".
    //
    // If, after the algorithm, there is more than one choice remaining, prefer
    // the one that came first in the list of available options.
    available.headOption
  }
}
