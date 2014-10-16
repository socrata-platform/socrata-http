import sbt._
import Keys._

object Build extends sbt.Build {
  lazy val build = Project(
    "socrata-http",
    file("."),
    settings = BuildSettings.buildSettings ++ Seq(
      autoScalaLibrary := false
    )
  ).dependsOn(socrataHttpCommon, socrataHttpServer, socrataHttpJetty, socrataHttpCuratorBroker, socrataHttpClient)
   .aggregate(socrataHttpCommon, socrataHttpServer, socrataHttpJetty, socrataHttpCuratorBroker, socrataHttpClient)

  lazy val socrataHttpCommon = Project(
    "socrata-http-common",
    file("socrata-http-common"),
    settings = SocrataHttpCommon.settings
  )

  lazy val socrataHttpServer = Project(
    "socrata-http-server",
    file("socrata-http-server"),
    settings = SocrataHttpServer.settings
  ) dependsOn(socrataHttpCommon)

  lazy val socrataHttpJetty = Project(
    "socrata-http-jetty",
    file("socrata-http-jetty"),
    settings = SocrataHttpJetty.settings
  ) dependsOn(socrataHttpServer)

  lazy val socrataHttpCuratorBroker = Project(
    "socrata-http-curator-broker",
    file("socrata-http-curator-broker"),
    settings = SocrataHttpCuratorBroker.settings
  ) dependsOn(socrataHttpJetty)

  lazy val socrataHttpClient = Project(
    "socrata-http-client",
    file("socrata-http-client"),
    settings = SocrataHttpClient.settings
  ) dependsOn(socrataHttpCommon)
}
