import sbt._

object Dependencies {
  val apacheHttpComponentsVersion = "4.2.5"
  val apacheHttpClient = "org.apache.httpcomponents" % "httpclient" % apacheHttpComponentsVersion exclude ("commons-logging", "commons-logging")
  val apacheHttpMime = "org.apache.httpcomponents" % "httpmime" % apacheHttpComponentsVersion exclude ("commons-logging", "commons-logging")

  val commonsLang = "commons-lang" % "commons-lang" % "2.4"

  val curatorDiscovery = "com.netflix.curator" % "curator-x-discovery" % "1.3.3"

  val javaxServlet = "javax.servlet" % "servlet-api" % "2.5"

  val jettyVersion = "7.5.1.v20110908"
  val jettyJmx = "org.eclipse.jetty" % "jetty-jmx" % jettyVersion
  val jettyServer = "org.eclipse.jetty" % "jetty-server" % jettyVersion
  val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % jettyVersion

  val rojomaJson = "com.rojoma" %% "rojoma-json" % "[2.4.0, 3.0.0)"

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.10.1"

  val scalaTest = "org.scalatest" %% "scalatest" % "1.9.1"

  def scalaReflect(scalaVersion: String) = "org.scala-lang" % "scala-reflect" % scalaVersion

  val simpleArm = "com.rojoma" %% "simple-arm" % "1.1.10"

  val slf4jVersion = "1.7.5"
  val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % slf4jVersion
  val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jVersion

  val socrataUtils = "com.socrata" %% "socrata-utils" % "[0.7.0,1.0.0)"
}
