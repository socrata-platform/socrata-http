package com.socrata.http
package util

import javax.servlet.http.HttpServletRequest
import util.RequestUtils.PimpedOutRequest

object RequestUtils {
  class PimpedOutRequest(underlying: HttpServletRequest) {
    def hostname =
      Option(underlying.getHeader("X-Socrata-Host")).getOrElse(
        Option(underlying.getHeader("Host")).getOrElse("")).split(':').head
  }

  implicit def pimpMyRequests(request: HttpServletRequest) = new PimpedOutRequest(request)
}
