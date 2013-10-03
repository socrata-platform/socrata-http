import sbt._
import Keys._

import Dependencies._

object SocrataHttpCommon {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      commonsCodec,
      commonsLang,
      slf4jApi,
      simpleArm,
      rojomaJson,
      scalaCheck % "test"
    )
  )
}
