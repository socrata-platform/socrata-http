package com.socrata.http.util

import java.lang.String
import java.text.SimpleDateFormat
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.text.ParseException
import java.util.Date

/**
 * Handle and validate conditional requests
 */

object ETagRequestHandler {
  val IMS_FORMAT:SimpleDateFormat  = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

  def isValidETag(inmHeader:String, objectEtag:String):Boolean = {
    if (inmHeader != null && objectEtag != null) {
      if (inmHeader.equals(objectEtag)) {
        return true
      }
      for (tag <- inmHeader.split(",")) {
        if (tag.replaceAll("\"", "") == objectEtag) {
          // Valid ETag
          true
        }
      }
    }
    false
  }

  def isValidIMS(imsHeader:String, objectMtime:Date):Boolean = {
    if (imsHeader != null && objectMtime != null) {
      try {
        val ims: Date = IMS_FORMAT.parse(imsHeader)
        if (ims.getTime >= objectMtime.getTime) {
          // Valid If Modified Since header
          return true
        }
      }
      catch {
        case e: ParseException => {
          // noop
        }
      }
    }
    false
  }

  /**
   * Validate a request against a some object etag and modification times
   * @param request
   * @param objectETag
   * @param objectMtime
   * @return
   */
  def isValid(request:HttpServletRequest, objectETag:String, objectMtime:Date):Boolean = {
    val inm: String = request.getHeader("If-None-Match")
    val ims: String = request.getHeader("If-Modified-Since")
    isValidETag(inm, objectETag) || isValidIMS(ims, objectMtime)
  }

  /**
   * Handle a conditional request entirely; returning true if the request is a valid conditional
   * request, setting the 304 along the way. If the request is not a valid conditional we just set
   * the right ETag and Last-Modified headers.
   *
   * If this is a valid conditional request; the caller should be able to completely short-circuit
   * the request and return nothing but a 304.
   *
   * @param request HttpServletRequest used to pull the If-None-Match and If-Modified-Since headers
   * @param response Response which we can modify
   * @param objectETag the etag, or hash of some object
   * @param objectMtime the last modification time of some object
   * @return true if this is a valid conditional and the request can be short-circuited
   */
  def handleConditional(request:HttpServletRequest, response:HttpServletResponse,  objectETag:String, objectMtime:Date):Boolean = {
    if (isValid(request, objectETag, objectMtime))  {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED)
      true
    } else {
      if (objectETag != null)
        response.setHeader("ETag", objectETag)
      if (objectMtime != null)
        response.setHeader("Last-Modified", IMS_FORMAT.format(objectMtime))
      false
    }
  }
}

