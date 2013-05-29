package com.socrata.http.util

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.WordSpec
import java.util.Date

class ETagRequestHandlerTest extends WordSpec with ShouldMatchers {

  "The etag request handler" should {
    "return true if we have a valid etag" in {
      ETagRequestHandler.isValidETag("bar", "bar")
      ETagRequestHandler.isValidETag("\"bar\"", "bar")
      ETagRequestHandler.isValidETag("\"foo\", \"bar\"", "bar")
    }

    "return false if we have an invalid etag" in {
      ETagRequestHandler.isValidETag("bar", "goats") should be (false)
      ETagRequestHandler.isValidETag(null, "goats") should be (false)
      ETagRequestHandler.isValidETag("bar", null) should be (false)


    }

    "return true if we have a valid last modified header" in {
      ETagRequestHandler.isValidIMS(ETagRequestHandler.IMS_FORMAT.format(new Date(1000)), new Date(0))
      ETagRequestHandler.isValidIMS(ETagRequestHandler.IMS_FORMAT.format(new Date(1000)), new Date(1000))
    }

    "return false if we have an invalid last modified header" in {
      ETagRequestHandler.isValidIMS(ETagRequestHandler.IMS_FORMAT.format(new Date(100)), new Date(1000)) should be (false)
      ETagRequestHandler.isValidIMS("This date does not look good", new Date(1000)) should be (false)
      ETagRequestHandler.isValidIMS(null, new Date(0)) should be (false)

    }


  }

}
