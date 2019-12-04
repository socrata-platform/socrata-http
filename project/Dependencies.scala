import sbt._

object Dependencies {
  val apacheHttpComponentsVersion = "4.3.3"
  val apacheHttpClient = "org.apache.httpcomponents" % "httpclient" % apacheHttpComponentsVersion exclude ("commons-logging", "commons-logging")
  val apacheHttpMime = "org.apache.httpcomponents" % "httpmime" % apacheHttpComponentsVersion exclude ("commons-logging", "commons-logging")

  val commonsLang = "commons-lang" % "commons-lang" % "2.4"

  val commonsCodec = "commons-codec" % "commons-codec" % "1.8"

  val commonsIo = "commons-io" % "commons-io" % "2.4"

  val curatorDiscovery = "org.apache.curator" % "curator-x-discovery" % "2.6.0"

  val jackson = "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13"

  val javaxServlet = "javax.servlet" % "javax.servlet-api" % "3.1.0"

  val jettyVersion = "9.2.2.v20140723"
  val jettyJmx = "org.eclipse.jetty" % "jetty-jmx" % jettyVersion
  val jettyServer = "org.eclipse.jetty" % "jetty-server" % jettyVersion
  val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % jettyVersion
  val jettyServlets = "org.eclipse.jetty" % "jetty-servlets" % jettyVersion

  val rojomaJson = "com.rojoma" %% "rojoma-json" % "[2.4.3, 3.0.0)"
  val rojomaJsonV3 = "com.rojoma" %% "rojoma-json-v3" % "[3.2.0, 4.0.0)"
  val rojomaJsonJackson = "com.rojoma" %% "rojoma-json-v3-jackson" % "[1.0.0, 2.0.0)"

  val jodaConvert = "org.joda" % "joda-convert" % "1.2"
  val jodaTime = "joda-time" % "joda-time" % "2.6"

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4"

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.0"

  def scalaReflect(scalaVersion: String) = "org.scala-lang" % "scala-reflect" % scalaVersion

  val simpleArm = "com.rojoma" %% "simple-arm-v2" % "[2.0.0,3.0.0)"

  val slf4jVersion = "1.7.7"
  val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % slf4jVersion
  val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jVersion

  val socrataUtils = "com.socrata" %% "socrata-utils" % "0.11.0"
  val socrataThirdpartyUtils = "com.socrata" %% "socrata-thirdparty-utils" % "5.0.0"

  val typesafeConfig = "com.typesafe" % "config" % "1.2.1"

}
