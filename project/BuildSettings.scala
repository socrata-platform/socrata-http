import sbt._
import Keys._

import com.socrata.cloudbeessbt.SocrataCloudbeesSbt
import com.typesafe.tools.mima.plugin.MimaKeys._

object BuildSettings {
  val cloudbees = "https://repo.socrata.com/"
  val cloudbeesSnapshots = "snapshots" at cloudbees + "libs-snapshot"
  val cloudbeesReleases = "releases" at cloudbees + "libs-release"

  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ SocrataCloudbeesSbt.socrataBuildSettings ++ Seq(
    scalaVersion := "2.11.7",
    crossScalaVersions := Seq("2.10.4", scalaVersion.value)
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ SocrataCloudbeesSbt.socrataProjectSettings() ++ Seq(
    previousArtifact <<= (scalaBinaryVersion, name) { (sv, name) => None /* Some("com.socrata" % (name + "_" + sv) % "2.3.0") */ },
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
    ),
    libraryDependencies ++= Seq(
      Dependencies.scalaTest % "test"
    ),
    libraryDependencies <++=(scalaVersion) {
      case "2.10.4" => Seq.empty
      case _ => Seq(
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
      )
    },
    scalacOptions ++= Seq("-language:implicitConversions")
  )
}
