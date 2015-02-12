import sbt._
import Keys._

import Dependencies._

object SocrataHttpInternal {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      javaxServlet % "provided",
      simpleArm,
      slf4jApi,
      scalaCheck % "test",
      slf4jSimple % "test",
      streamLib,
      highScale
    )
  )
}
