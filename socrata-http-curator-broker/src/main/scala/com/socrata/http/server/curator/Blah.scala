package com.socrata.http.server.curator

import scala.collection.JavaConverters._
import javax.servlet.http.HttpServletRequest

import com.socrata.http.common.AuxiliaryData
import com.socrata.http.server.SocrataServerJetty
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder
import com.rojoma.json.v3.interpolation._

object Blah extends App {
  val cf = CuratorFrameworkFactory.builder().connectString("192.168.100.10:2181").namespace("testing").retryPolicy(new RetryNTimes(100, 100)).build()
  cf.start()
  val sd = ServiceDiscoveryBuilder.builder(classOf[AuxiliaryData]).client(cf).basePath("/services").build()
  sd.start()
  if(true) {
    for(instance <- sd.queryForInstances("testserv").asScala) {
      println(instance.getPayload.json)
    }
  } else {
    val aux = new AuxiliaryData(None, j"{hello:'world'}")
    val b = new CuratorBroker(sd, "localhost", "testserv", Some(aux))
    def handler(req: HttpServletRequest) = ???
    val serv = new SocrataServerJetty(handler _, SocrataServerJetty.defaultOptions.withBroker(b))
    serv.run()
  }
}
