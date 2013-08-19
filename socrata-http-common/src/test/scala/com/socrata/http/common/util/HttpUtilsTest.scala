package com.socrata.http.common.util

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite

class HttpUtilsTest extends FunSuite with MustMatchers {
  test("parseAccept can parse the examples from RFC2616") {
    HttpUtils.parseAccept("audio/*; q=0.2, audio/basic") must equal (Seq(
      HttpUtils.MediaRange("audio", "*", Nil, 0.2, Nil),
      HttpUtils.MediaRange("audio", "basic", Nil, 1.0, Nil)
    ))
    HttpUtils.parseAccept("text/plain; q=0.5, text/html, text/x-dvi; q=0.8, text/x-c") must equal (Seq(
      HttpUtils.MediaRange("text", "plain", Nil, 0.5, Nil),
      HttpUtils.MediaRange("text", "html", Nil, 1.0, Nil),
      HttpUtils.MediaRange("text", "x-dvi", Nil, 0.8, Nil),
      HttpUtils.MediaRange("text", "x-c", Nil, 1.0, Nil)
    ))
    HttpUtils.parseAccept("text/*, text/html, text/html;level=1, */*") must equal (Seq(
      HttpUtils.MediaRange("text", "*", Nil, 1.0, Nil),
      HttpUtils.MediaRange("text", "html", Nil, 1.0, Nil),
      HttpUtils.MediaRange("text", "html", Seq("level" -> "1"), 1.0, Nil),
      HttpUtils.MediaRange("*", "*", Nil, 1.0, Nil)
    ))
    HttpUtils.parseAccept("text/*;q=0.3, text/html;q=0.7, text/html;level=1, text/html;level=2;q=0.4, */*;q=0.5") must equal (Seq(
      HttpUtils.MediaRange("text", "*", Nil, 0.3, Nil),
      HttpUtils.MediaRange("text", "html", Nil, 0.7, Nil),
      HttpUtils.MediaRange("text", "html", Seq("level" -> "1"), 1.0, Nil),
      HttpUtils.MediaRange("text", "html", Seq("level" -> "2"), 0.4, Nil),
      HttpUtils.MediaRange("*", "*", Nil, 0.5, Nil)
    ))
  }

  test("parseAccept of `*' is the same as `*/*'") {
    HttpUtils.parseAccept("*") must equal (Seq(HttpUtils.MediaRange("*","*",Nil,1.0,Nil)))
    HttpUtils.parseAccept("*;gnu=5") must equal (Seq(HttpUtils.MediaRange("*","*",Seq("gnu" -> "5"),1.0,Nil)))
    HttpUtils.parseAccept("*,text/plain") must equal (Seq(
      HttpUtils.MediaRange("*","*",Nil,1.0,Nil),
      HttpUtils.MediaRange("text","plain",Nil,1.0,Nil)
    ))
  }

  test("parseAcceptCharset can parse the examples from RFC2616") {
    HttpUtils.parseAcceptCharset("iso-8859-5, unicode-1-1;q=0.8") must equal (Seq(
      HttpUtils.CharsetRange("iso-8859-5", 1.0),
      HttpUtils.CharsetRange("unicode-1-1", 0.8)
    ))
  }

  test("parseAcceptLanguage can parse the examples from RFC2616") {
    HttpUtils.parseAcceptLanguage("da, en-gb;q=0.8, en;q=0.7") must equal (Seq(
      HttpUtils.LanguageRange(Seq("da"), 1.0),
      HttpUtils.LanguageRange(Seq("en", "gb"), 0.8),
      HttpUtils.LanguageRange(Seq("en"), 0.7)
    ))
  }
}
