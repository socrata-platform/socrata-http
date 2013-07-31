package com.socrata.http.server.curator

import scala.language.reflectiveCalls
import com.socrata.http.server.ServerBroker
import com.netflix.curator.x.discovery.{UriSpec, ServiceInstance, ServiceDiscovery}

class CuratorBroker[T](serviceDiscovery: ServiceDiscovery[T], address: String, serviceName: String, auxData: Option[T]) extends ServerBroker {
  type Cookie = ServiceInstance[T]

  val simpleUriSpec = new UriSpec("{scheme}://{address}:{port}/")

  def register(port: Int): Cookie = {
    val instance = ServiceInstance.builder[T].
      name(serviceName).
      address(address).
      port(port).
      uriSpec(simpleUriSpec).
      payload(auxData.map(_.asInstanceOf[AnyRef]).orNull.asInstanceOf[T]).
      build()

    serviceDiscovery.registerService(instance)

    instance
  }

  def deregister(cookie: Cookie) {
    serviceDiscovery.unregisterService(cookie)
  }
}
