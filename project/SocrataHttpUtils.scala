import sbt._
import Keys._

object SocrataHttpUtils {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      "commons-lang" % "commons-lang" % "2.4",
      "org.slf4j" % "slf4j-api" % BuildSettings.slf4jVersion,
      "javax.servlet" % "servlet-api" % "2.5" % "provided"
    ),
    libraryDependencies <++= (scalaVersion) {
      case s if s.startsWith("2.10.") =>
        Seq("org.scala-lang" % "scala-reflect" % s)
      case _ =>
        Nil
    }
  )
}
