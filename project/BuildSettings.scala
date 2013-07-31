import sbt._
import Keys._

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact

object BuildSettings {
  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ Seq(
    organization := "com.socrata",
    version := "2.0.0-SNAPSHOT",
    scalaVersion := "2.10.2"
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ mimaDefaultSettings ++ Seq(
    previousArtifact <<= (scalaBinaryVersion, name) { (sv, name) => Some("com.socrata" % (name + "_" + sv) % "1.2.0") },
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
    ),
    libraryDependencies ++= Seq(
      Dependencies.scalaTest % "test"
    ),
    scalacOptions ++= Seq("-encoding", "UTF-8", "-g:vars", "-deprecation", "-feature", "-language:implicitConversions"),
    javacOptions ++= Seq("-encoding", "UTF-8", "-g", "-Xlint:unchecked", "-Xlint:deprecation", "-Xmaxwarns", "999999"),
    ivyXML := // com.rojoma and com.socrata have binary compat guarantees
      <dependencies>
        <conflict org="com.socrata" manager="latest-compatible"/>
        <conflict org="com.rojoma" manager="latest-compatible"/>
      </dependencies>
  )
}
