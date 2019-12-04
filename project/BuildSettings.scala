import sbt._
import Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

object BuildSettings {
  val buildSettings: Seq[Setting[_]] = Seq(
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.10.4", "2.11.7", scalaVersion.value),
    organization := "com.socrata",
    resolvers += "socrata releases" at "https://repo.socrata.com/artifactory/libs-release"
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ Seq(
    mimaPreviousArtifacts := Set( /*"com.socrata" %% name % "2.3.0" */),
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
    ),
    libraryDependencies ++= Seq(
      Dependencies.scalaTest % "test"
    ),
    libraryDependencies ++= {
      scalaVersion.value match {
        case "2.10.4" => Seq.empty
        case _ => Seq(
          "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
        )
      }
    },
    scalacOptions ++= Seq("-language:implicitConversions")
  )
}
