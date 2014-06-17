import sbt._
import Keys._

import com.socrata.cloudbeessbt.SocrataCloudbeesSbt
import com.typesafe.tools.mima.plugin.MimaKeys._

object BuildSettings {
  val cloudbees = "https://repository-socrata-oss.forge.cloudbees.com/"
  val cloudbeesSnapshots = "snapshots" at cloudbees + "snapshot"
  val cloudbeesReleases = "releases" at cloudbees + "release"

  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ SocrataCloudbeesSbt.socrataBuildSettings ++ Seq(
    scalaVersion := "2.10.4"
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ SocrataCloudbeesSbt.socrataProjectSettings() ++ Seq(
    previousArtifact <<= (scalaBinaryVersion, name) { (sv, name) => None /* Some("com.socrata" % (name + "_" + sv) % "1.2.0") */ },
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
    ),
    libraryDependencies ++= Seq(
      Dependencies.scalaTest % "test"
    ),
    scalacOptions ++= Seq("-language:implicitConversions")
  )
}
