ThisBuild / scalaVersion := "2.12.8"

ThisBuild / crossScalaVersions := Seq("2.10.4", "2.11.7", scalaVersion.value)

ThisBuild / organization := "com.socrata"

ThisBuild / resolvers += "socrata releases" at "https://repo.socrata.com/artifactory/libs-release"

ThisBuild / Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oFD")

lazy val socrataHttpCommon = project in file("socrata-http-common")

lazy val socrataHttpServer = (project in file("socrata-http-server")).
  dependsOn(socrataHttpCommon)

lazy val socrataHttpJetty = (project in file("socrata-http-jetty")).
  dependsOn(socrataHttpServer)

lazy val socrataHttpCuratorBroker = (project in file("socrata-http-curator-broker")).
  dependsOn(socrataHttpJetty)

lazy val socrataHttpClient = (project in file("socrata-http-client")).
  dependsOn(socrataHttpCommon)

publish / skip := true
