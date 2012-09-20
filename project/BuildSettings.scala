import sbt._
import Keys._

import com.socrata.socratasbt.SocrataSbt._
import com.socrata.socratasbt.CheckClasspath

object BuildSettings {
  val buildSettings: Seq[Setting[_]] = Defaults.defaultSettings ++ socrataBuildSettings ++ Seq(
    scalaVersion := "2.9.2",
    scalacOptions += "-Ydependent-method-types", // won't be necesary with Scala 2.10 (required for Shapeless in 2.9)
    compile in Compile <<= (compile in Compile) dependsOn (CheckClasspath.Keys.failIfConflicts in Compile),
    compile in Test <<= (compile in Test) dependsOn (CheckClasspath.Keys.failIfConflicts in Test),
    testOptions in Test ++= Seq(
      Tests.Argument("-oFD")
    )
  )
}
