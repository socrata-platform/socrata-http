import sbt._
import Keys._

object SocrataHttpJetty {
  val jettyVersion = "7.5.1.v20110908"

  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.socrata" %% "socrata-utils" % "[0.6.1,1.0.0)",
      "org.eclipse.jetty" % "jetty-jmx" % jettyVersion,
      "org.eclipse.jetty" % "jetty-server" % jettyVersion,
      "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
      "org.slf4j" % "slf4j-api" % BuildSettings.slf4jVersion
    )
  )
}
