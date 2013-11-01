package com.socrata.http.server.util.handlers

import com.socrata.http.server.HttpService
import javax.servlet.http.HttpServletRequest

class ThreadRenamingHandler(underlying: HttpService) extends HttpService {
  def apply(req: HttpServletRequest) = { resp =>
    val oldName = Thread.currentThread.getName
    try {
      Thread.currentThread.setName(Thread.currentThread.getId + " / " + req.getMethod + " " + req.getRequestURI)
      underlying(req)(resp)
    } finally {
      Thread.currentThread.setName(oldName)
    }
  }
}

object ThreadRenamingHandler {
  def apply(handler: HttpService): ThreadRenamingHandler = new ThreadRenamingHandler(handler)
}
