import sbt._
import Keys._

import Dependencies._

object SocrataHttpJetty {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    name := "socrata-http-jetty",
    libraryDependencies ++= Seq(
      socrataUtils,
      jettyJmx,
      jettyServer,
      jettyServlet,
      jettyServlets,
      slf4jApi
    )
  )
}
