package com.socrata.http.server.livenesscheck

import com.socrata.thirdparty.typesafeconfig.ConfigClass
import com.typesafe.config.Config

class LivenessCheckConfig(config: Config, root: String) extends ConfigClass(config, root) {
  val bindToAdvertisedInterface = getBoolean("bind-to-advertised-interface")
  val port = getInt("port")
  val address = getString("address")
}
