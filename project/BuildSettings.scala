import sbt._
import Keys._

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
import eu.diversit.sbt.plugin.WebDavPlugin._

object BuildSettings {
  val cloudbees = "https://repository-socrata-oss.forge.cloudbees.com/"
  val cloudbeesSnapshots = "snapshots" at cloudbees + "snapshot"
  val cloudbeesReleases = "releases" at cloudbees + "release"

  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ WebDav.scopedSettings ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ Seq(
    organization := "com.socrata",
    version := "1.3.1",
    scalaVersion := "2.10.0",
    crossScalaVersions := Seq("2.8.1", "2.9.2", "2.10.0"),
    resolvers <++= version { v =>
      if(v.endsWith("SNAPSHOT")) Seq(cloudbeesSnapshots)
      else Nil
    },
    // TODO: remove this once we're publishing to maven central so that
    // it doesn't end up in the POM file.
    resolvers += cloudbeesReleases,
    credentials ++= List(new File("/private/socrata-oss/maven-credentials")).flatMap { f =>
      if(f.exists) Some(Credentials(f)) else None
    },
    publishTo <<= version { v =>
      if(v.endsWith("SNAPSHOT")) Some(cloudbeesSnapshots)
      else Some(cloudbeesReleases)
    }
  )

  val projectSettings: Seq[Setting[_]] = buildSettings ++ mimaDefaultSettings ++ Seq(
    previousArtifact <<= (scalaBinaryVersion, name) { (sv, name) => Some("com.socrata" % (name + "_" + sv) % "1.2.0") },
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
    ),
    libraryDependencies <+= (scalaVersion) {
      case "2.8.1" => "org.scalatest" % "scalatest_2.8.1" % "1.8" % "test"
      case _ => "org.scalatest" %% "scalatest" % "1.9.1" % "test"
    },
    scalacOptions <++= (scalaVersion) map {
      case s if s.startsWith("2.8.") => Seq("-encoding", "UTF-8", "-g", "-unchecked", "-deprecation")
      case s if s.startsWith("2.9.") => Seq("-encoding", "UTF-8", "-g:vars", "-unchecked", "-deprecation")
      case s if s.startsWith("2.10.") => Seq("-encoding", "UTF-8", "-g:vars", "-deprecation", "-feature", "-language:implicitConversions")
    },
    javacOptions ++= Seq("-encoding", "UTF-8", "-g", "-Xlint:unchecked", "-Xlint:deprecation", "-Xmaxwarns", "999999"),
    unmanagedSourceDirectories in Compile <++= (scalaVersion, scalaSource in Compile)(versionSpecificSourceDir),
    unmanagedSourceDirectories in Test <++= (scalaVersion, scalaSource in Test)(versionSpecificSourceDir),
    ivyXML := // com.rojoma and com.socrata have binary compat guarantees
      <dependencies>
        <conflict org="com.socrata" manager="latest-compatible"/>
        <conflict org="com.rojoma" manager="latest-compatible"/>
      </dependencies>
  )

  val slf4jVersion = "1.7.5"

  def versionSpecificSourceDir(scalaVersion: String, scalaSource: File) = scalaVersion match {
    case s if s.startsWith("2.10.") => Seq(scalaSource.getParentFile / "scala-2.10")
    case _ => Nil
  }
}
