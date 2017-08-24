package com.socrata.http.util

import org.scalatest.{MustMatchers, WordSpec}
import java.util.Date
import com.socrata.http.server.util

class ETagRequestHandlerTest extends WordSpec with MustMatchers {

  "The etag request handler" must {
    "return true if we have a valid etag" in {
      util.ETagRequestHandler.isValidETag("bar", "bar")
      util.ETagRequestHandler.isValidETag("\"bar\"", "bar")
      util.ETagRequestHandler.isValidETag("\"foo\", \"bar\"", "bar")
    }

    "return false if we have an invalid etag" in {
      util.ETagRequestHandler.isValidETag("bar", "goats") must be (false)
      util.ETagRequestHandler.isValidETag(null, "goats") must be (false)
      util.ETagRequestHandler.isValidETag("bar", null) must be (false)


    }

    "return true if we have a valid last modified header" in {
      util.ETagRequestHandler.isValidIMS(util.ETagRequestHandler.IMS_FORMAT.format(new Date(1000)), new Date(0))
      util.ETagRequestHandler.isValidIMS(util.ETagRequestHandler.IMS_FORMAT.format(new Date(1000)), new Date(1000))
    }

    "return false if we have an invalid last modified header" in {
      util.ETagRequestHandler.isValidIMS(util.ETagRequestHandler.IMS_FORMAT.format(new Date(100)), new Date(1000)) must be (false)
      util.ETagRequestHandler.isValidIMS("This date does not look good", new Date(1000)) must be (false)
      util.ETagRequestHandler.isValidIMS(null, new Date(0)) must be (false)
    }

  }

}
