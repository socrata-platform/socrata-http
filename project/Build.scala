import sbt._
import Keys._

object Build extends sbt.Build {
  lazy val build = Project(
    "socrata-http",
    file("."),
    settings = BuildSettings.buildSettings ++ Seq(
      autoScalaLibrary := false
    )
  ).dependsOn(allOtherProjects.map(p => p:ClasspathDep[ProjectReference]): _*)
   .aggregate(allOtherProjects: _*)

  private def allOtherProjects =
    for {
      method <- getClass.getDeclaredMethods.toSeq
      if method.getParameterTypes.isEmpty && classOf[Project].isAssignableFrom(method.getReturnType) && method.getName != "build"
    } yield method.invoke(this).asInstanceOf[Project] : ProjectReference

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
