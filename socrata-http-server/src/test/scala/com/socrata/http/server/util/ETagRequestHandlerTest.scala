package com.socrata.http.util

import org.scalatest.{ShouldMatchers, WordSpec}
import java.util.Date
import com.socrata.http.server.util

class ETagRequestHandlerTest extends WordSpec with ShouldMatchers {

  "The etag request handler" should {
    "return true if we have a valid etag" in {
      util.ETagRequestHandler.isValidETag("bar", "bar")
      util.ETagRequestHandler.isValidETag("\"bar\"", "bar")
      util.ETagRequestHandler.isValidETag("\"foo\", \"bar\"", "bar")
    }

    "return false if we have an invalid etag" in {
      util.ETagRequestHandler.isValidETag("bar", "goats") should be (false)
      util.ETagRequestHandler.isValidETag(null, "goats") should be (false)
      util.ETagRequestHandler.isValidETag("bar", null) should be (false)


    }

    "return true if we have a valid last modified header" in {
      util.ETagRequestHandler.isValidIMS(util.ETagRequestHandler.ImsFormat.format(new Date(1000)), new Date(0))
      util.ETagRequestHandler.isValidIMS(util.ETagRequestHandler.ImsFormat.format(new Date(1000)), new Date(1000))
    }

    "return false if we have an invalid last modified header" in {
      util.ETagRequestHandler.isValidIMS(util.ETagRequestHandler.ImsFormat.format(new Date(100)), new Date(1000)) should be (false)
      util.ETagRequestHandler.isValidIMS("This date does not look good", new Date(1000)) should be (false)
      util.ETagRequestHandler.isValidIMS(null, new Date(0)) should be (false)

    }


  }

}
