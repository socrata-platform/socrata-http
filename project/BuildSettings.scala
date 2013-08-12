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
    version := "2.0.0-SNAPSHOT",
    scalaVersion := "2.10.2",
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
