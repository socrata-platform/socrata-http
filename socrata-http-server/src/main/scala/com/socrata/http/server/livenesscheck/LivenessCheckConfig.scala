package com.socrata.http.server.livenesscheck

import com.socrata.thirdparty.typesafeconfig.ConfigClass
import com.typesafe.config.Config

class LivenessCheckConfig(config: Config, root: String) extends ConfigClass(config, root) {
  /** Listen on port if specified, otherwise use ephemeral port. */
  val port = optionally(getInt("port"))
  /** Bind to address of the specific hostname or IP if specified, otherwise use wildcard. This should be set on
    * systems with multiple interfaces on the same network or you may risk sending responses from the wrong IP.
    */
  val address = optionally(getString("address"))
}
