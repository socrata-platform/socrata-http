import sbt._
import Keys._

import Dependencies._

object SocrataHttpCommon {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    name := "socrata-http-common",
    libraryDependencies ++= Seq(
      commonsCodec,
      commonsLang,
      jodaConvert,
      jodaTime,
      slf4jApi,
      simpleArm,
      rojomaJson,
      rojomaJsonV3,
      rojomaJsonJackson,
      jackson,
      scalaCheck % "test"
    )
  )
}
