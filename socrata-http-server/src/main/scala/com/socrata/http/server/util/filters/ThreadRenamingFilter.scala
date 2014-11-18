package com.socrata.http.server.util.filters

import com.socrata.http.server.{Filter, HttpResponse}
import javax.servlet.http.HttpServletRequest

abstract class ThreadRenamingFilter[InDown, OutUp] extends Filter[InDown, OutUp, InDown, OutUp] {
  def apply(request: InDown, service: (InDown) => OutUp): OutUp = {
    val me = Thread.currentThread
    val oldName = me.getName
    try {
      me.setName(newName(request))
      service(request)
    } finally {
      me.setName(oldName)
    }
  }

  def newName(in: InDown): String
}
