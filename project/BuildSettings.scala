import com.typesafe.tools.mima.plugin.MimaKeys._
import sbt.Keys._
import sbt._

object BuildSettings {
  val buildSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq(
    // TODO: enable scaalstyle build failures
    com.socrata.sbtplugins.StylePlugin.StyleKeys.styleFailOnError in Compile := false,
    // TODO: enable code coverage build failures
    scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := false,
    scalaVersion := "2.10.4"
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ Seq(
    previousArtifact <<= (scalaBinaryVersion, name) { (sv, name) => None /* Some("com.socrata" % (name + "_" + sv) % "2.3.0") */ },
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
    ),
    libraryDependencies ++= Seq(
    ),
    scalacOptions ++= Seq("-language:implicitConversions")
  )
}
