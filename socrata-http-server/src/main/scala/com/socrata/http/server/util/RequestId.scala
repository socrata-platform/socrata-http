package com.socrata.http.server.util

import javax.servlet.http.HttpServletRequest
import org.slf4j.MDC

/**
 * Utilities for obtaining a universal request ID for socrata-http apps.
 */
object RequestId {
  type RequestId = String

  val ReqIdHeader = "X-Socrata-RequestId"
  private val secureRandom = new java.security.SecureRandom

  /**
   * Obtains a RequestId from an HTTP request, generating one if not present.
   * Also inserts the RequestId into MDC for logging if not present.
   * @param req the servlet request object
   * @return the RequestId from the request or a generated one
   */
  def getFromRequest(req: HttpServletRequest): RequestId =
    Option(req.getHeader(ReqIdHeader)).getOrElse(generateAndPut())

  /**
   * Generates a random Request ID.
   * NOTE: this is probably good enough but should we consider using UUIDs?
   */
  def generate(): RequestId = math.BigInt(128, secureRandom).toString(36)

  private def generateAndPut(): RequestId = {
    val reqId = generate()
    MDC.put(ReqIdHeader, reqId)
    reqId
  }
}
