package com.socrata.http.server

import com.socrata.util.advertisement.{AdvertisementRegistration, Advertiser}

class AdvertiserServerBroker(advertiser: Advertiser, host: String) extends ServerBroker {
  type Cookie = AdvertisementRegistration

  def register(port: Int) = advertiser.advertise(host + ":" + port)
  def deregister(registration: AdvertisementRegistration) = registration.stopAdvertising()
}
