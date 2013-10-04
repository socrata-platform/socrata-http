package com.socrata.http.server.util

import com.socrata.http.common.util.HttpUtils
import org.apache.commons.codec.binary.Base64

object EntityTagRenderer extends (EntityTag => String) {
  def apply(etag: EntityTag): String = etag match {
    case s: StrongEntityTag =>
      HttpUtils.quote(Base64.encodeBase64URLSafeString(s.asBytesUnsafe))
    case w: WeakEntityTag =>
      val res = new java.lang.StringBuilder(2 + w.asBytesUnsafe.length * 2) // bit of an over-estimate but oh well
      res.append("W/")
      HttpUtils.quoteInto(res, Base64.encodeBase64URLSafeString(w.asBytesUnsafe))
      res.toString
  }
}
