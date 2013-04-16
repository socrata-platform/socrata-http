import sbt._
import Keys._

object SocrataHttpCuratorBroker {
  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.netflix.curator" % "curator-x-discovery" % "1.3.3"
    )
  )
}
