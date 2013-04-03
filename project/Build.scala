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

  lazy val socrataHttpJetty = Project(
    "socrata-http-jetty",
    file("socrata-http-jetty"),
    settings = SocrataHttpJetty.settings
  ) dependsOn(socrataHttpUtils)

  lazy val socrataHttpUtils = Project(
    "socrata-http-utils",
    file("socrata-http-utils"),
    settings = SocrataHttpUtils.settings
  )
}
