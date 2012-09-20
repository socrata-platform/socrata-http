import sbt._

class Dependencies(slf4jVersion: String) {
  object versions {
    val jetty = "7.5.1.v20110908"
    val scalaCheck_28 = "1.8"
    val scalaCheck_29 = "1.9"
    val scalaTest = "1.7.2"
    val socrataUtils = "0.0.1"
    val slf4j = slf4jVersion
  }





  val jettyJmx = "org.eclipse.jetty" % "jetty-jmx" % versions.jetty
  val jettyServer = "org.eclipse.jetty" % "jetty-server" % versions.jetty
  val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % versions.jetty


  // scala's much better about binary compatibility these days, but
  // SBT still assumes subminor version changes need individual
  // artifacts.  This means that for at least some projects,
  // '%%'-magic doesn't work properly.

  def scalaCheck(implicit scalaVersion: String) = v match {
    case Scala28 => "org.scala-tools.testing" % "scalacheck_2.8.1" % versions.scalaCheck_28
    case Scala29 => "org.scala-tools.testing" % "scalacheck_2.9.0" % versions.scalaCheck_29
  }

  val scalaTest =
    "org.scalatest" %% "scalatest" % versions.scalaTest



  val slf4jProvidesLog4j = "org.slf4j" % "log4j-over-slf4j" % versions.slf4j
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % versions.slf4j

  val socrataUtils = "com.socrata" %% "socrata-utils" % versions.socrataUtils

  private sealed abstract class MajorScalaVersion
  private case object Scala28 extends MajorScalaVersion
  private case object Scala29 extends MajorScalaVersion

  private def v(implicit sv: String) =
    if(sv startsWith "2.8.") Scala28
    else if(sv startsWith "2.9.") Scala29
    else sys.error("Scala version unmapped: " + sv)
}

object Dependencies {
  def apply(slf4jVersion: String) = new Dependencies(slf4jVersion)
}
