package com.socrata.http.server.util.handlers

import com.socrata.http.server.{HttpRequest, HttpService}

class ThreadRenamingHandler(underlying: HttpService) extends HttpService {
  def apply(req: HttpRequest) = { resp =>
    req.resourceScope.open(new RenameHelper(req))
    underlying(req)(resp)
  }

  private class RenameHelper(req: HttpRequest) extends AutoCloseable {
    val thread = Thread.currentThread
    val oldName = thread.getName

    thread.setName(thread.getId + " / " + req.method + " " + req.requestPathStr)

    override def close() {
      thread.setName(oldName)
    }
  }
}

object ThreadRenamingHandler {
  def apply(handler: HttpService): ThreadRenamingHandler = new ThreadRenamingHandler(handler)
}
