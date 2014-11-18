package com.socrata.http.server.util.handlers

import com.socrata.http.server.{HttpRequest, HttpService}

class ThreadRenamingHandler(underlying: HttpService) extends HttpService {
  def apply(req: HttpRequest) = { resp =>
    val oldName = Thread.currentThread.getName
    try {
      Thread.currentThread.setName(Thread.currentThread.getId + " / " + req.method + " " + req.requestPathStr)
      underlying(req)(resp)
    } finally {
      Thread.currentThread.setName(oldName)
    }
  }
}

object ThreadRenamingHandler {
  def apply(handler: HttpService): ThreadRenamingHandler = new ThreadRenamingHandler(handler)
}
