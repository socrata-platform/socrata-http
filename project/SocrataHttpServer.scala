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
        slf4jApi,
        scalaCheck % "test"
      )
    },

    // macro-paradie macros
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin("org.scala-lang.plugins" % "macro-paradise_2.10.2" % "2.0.0-SNAPSHOT")
  )
}
