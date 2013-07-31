import sbt._
import Keys._

import Dependencies._

object SocrataHttpServer {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies <++= (scalaVersion) { sv =>
      Seq(
        javaxServlet % "provided",
        scalaReflect(sv),
        simpleArm,
        slf4jApi
      )
    }
  )
}
