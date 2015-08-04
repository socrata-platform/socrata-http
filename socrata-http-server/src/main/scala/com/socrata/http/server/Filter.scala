package com.socrata.http.server

trait Filter[-InDown, +OutUp, +OutDown, -InUp] extends ((InDown, Service[OutDown, InUp]) => OutUp) { self =>
  def apply(request: InDown, service: Service[OutDown, InUp]): OutUp

  def andThen[Req2, Rep2](next: Filter[OutDown, InUp, Req2, Rep2]): Filter[InDown, OutUp, Req2, Rep2] =
    new Filter[InDown, OutUp, Req2, Rep2] {
      def apply(request: InDown, service: Service[Req2, Rep2]) =
        self(request, new Service[OutDown, InUp] {
          def apply(outDown: OutDown) = next(outDown, service)
        })
    }

  def andThen(service: Service[OutDown, InUp]): Service[InDown,OutUp] = new Service[InDown, OutUp] {
    def apply(req: InDown) = self(req, service)
  }
}
