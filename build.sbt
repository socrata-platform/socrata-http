import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact

mimaDefaultSettings

name := "socrata-http"

organization := "com.socrata"

version := "1.1.1-SNAPSHOT"

previousArtifact <<= scalaBinaryVersion { sv => Some("com.socrata" % ("socrata-http_" + sv) % "1.1.0") }

scalaVersion := "2.10.0"

crossScalaVersions := Seq("2.8.1", "2.9.2", "2.10.0")

libraryDependencies <++= (scalaVersion) { scalaVersion =>
  val jettyVersion = "7.5.1.v20110908"
  Seq(
    "commons-lang" % "commons-lang" % "2.4",
    "org.eclipse.jetty" % "jetty-jmx" % jettyVersion,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "com.socrata" %% "socrata-utils" % "[0.6.1,1.0.0)",
    "org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "slf4j-simple" % "1.7.5" % "test"
  ) ++ (scalaVersion match {
    case s if s.startsWith("2.10.") => Seq("org.scala-lang" % "scala-reflect" % scalaVersion)
    case _ => Nil
  }) ++ (scalaVersion match {
    case "2.8.1" => Seq("org.scalatest" % "scalatest_2.8.1" % "1.8" % "test")
    case _ => Seq("org.scalatest" %% "scalatest" % "1.9.1" % "test")
  })
}

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-oFD")
)

scalacOptions <++= (scalaVersion) map {
  case s if s.startsWith("2.8.") => Seq("-encoding", "UTF-8", "-g", "-unchecked", "-deprecation")
  case s if s.startsWith("2.9.") => Seq("-encoding", "UTF-8", "-g:vars", "-unchecked", "-deprecation")
  case s if s.startsWith("2.10.") => Seq("-encoding", "UTF-8", "-g:vars", "-deprecation", "-feature", "-language:implicitConversions")
}

javacOptions ++= Seq("-encoding", "UTF-8", "-g", "-Xlint:unchecked", "-Xlint:deprecation", "-Xmaxwarns", "999999")

unmanagedSourceDirectories in Compile <++= (scalaVersion, scalaSource in Compile) { (sv, commonSource) =>
  if(sv.startsWith("2.10.")) Seq(commonSource.getParentFile / "scala-2.10")
  else Nil
}

unmanagedSourceDirectories in Test <++= (scalaVersion, scalaSource in Test) { (sv, commonSource) =>
  if(sv.startsWith("2.10.")) Seq(commonSource.getParentFile / "scala-2.10")
  else Nil
}


ivyXML := // com.rojoma and com.socrata have binary compat guarantees
  <dependencies>
    <conflict org="com.socrata" manager="latest-compatible"/>
    <conflict org="com.rojoma" manager="latest-compatible"/>
  </dependencies>
