import sbt._
import Keys._

import Dependencies._

object SocrataHttpClient {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      apacheHttpClient exclude ("commons-logging", "commons-logging"),
      commonsIo,
      apacheHttpMime,
      jclOverSlf4j,
      simpleArm,
      slf4jApi,
      scalaCheck % "test",
      slf4jSimple % "test"
    )
  )
}
