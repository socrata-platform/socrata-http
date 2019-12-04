lazy val build = (project in file(".")).
  settings(BuildSettings.buildSettings ++ Seq(
    autoScalaLibrary := false
  )).
  dependsOn(socrataHttpCommon, socrataHttpServer, socrataHttpJetty, socrataHttpCuratorBroker, socrataHttpClient).
  aggregate(socrataHttpCommon, socrataHttpServer, socrataHttpJetty, socrataHttpCuratorBroker, socrataHttpClient)

lazy val socrataHttpCommon = (project in file("socrata-http-common")).
  settings(SocrataHttpCommon.settings)

lazy val socrataHttpServer = (project in file("socrata-http-server")).
  settings(SocrataHttpServer.settings).
  dependsOn(socrataHttpCommon)

lazy val socrataHttpJetty = (project in file("socrata-http-jetty")).
  settings(SocrataHttpJetty.settings).
  dependsOn(socrataHttpServer)

lazy val socrataHttpCuratorBroker = (project in file("socrata-http-curator-broker")).
  settings(SocrataHttpCuratorBroker.settings).
  dependsOn(socrataHttpJetty)

lazy val socrataHttpClient = (project in file("socrata-http-client")).
  settings(SocrataHttpClient.settings).
  dependsOn(socrataHttpCommon)
