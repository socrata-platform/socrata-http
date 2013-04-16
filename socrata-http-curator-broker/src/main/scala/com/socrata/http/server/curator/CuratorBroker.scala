package com.socrata.http.server.curator

import com.socrata.http.server.ServerBroker
import com.netflix.curator.x.discovery.{UriSpec, ServiceInstance, ServiceDiscovery}

class CuratorBroker(serviceDiscovery: ServiceDiscovery[Void], address: String, serviceName: String) extends ServerBroker {
  type Cookie = ServiceInstance[Void]

  val simpleUriSpec = new UriSpec("{scheme}://{address}:{port}/")

  def register(port: Int): Cookie = {
    val instance = ServiceInstance.builder[Void].
      name(serviceName).
      address(address).
      port(port).
      uriSpec(simpleUriSpec).
      build()

    serviceDiscovery.registerService(instance)

    instance
  }

  def deregister(cookie: Cookie) {
    serviceDiscovery.unregisterService(cookie)
  }
}
