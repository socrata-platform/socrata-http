import sbt._
import Keys._

import Dependencies._

object SocrataHttpJetty {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      socrataUtils,
      jettyJmx,
      jettyServer,
      jettyServlet,
      slf4jApi
    )
  )
}
