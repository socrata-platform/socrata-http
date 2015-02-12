import sbt._
import Keys._

import Dependencies._

object SocrataHttpServer {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies <++= (scalaVersion) { sv =>
      Seq(
        javaxServlet % "provided",
        jodaConvert,
        jodaTime,
        scalaReflect(sv),
        simpleArm,
        slf4jApi,
        scalaCheck % "test"
      )
    },

    // macro-paradise macros
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
  )
}
