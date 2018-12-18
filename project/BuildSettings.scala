import sbt._
import Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

object BuildSettings {
  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ Seq(
    scalaVersion := "2.11.7",
    crossScalaVersions := Seq("2.10.4", scalaVersion.value),
    organization := "com.socrata",
    resolvers += "socrata releases" at "https://repo.socrata.com/artifactory/libs-release"
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ Seq(
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
