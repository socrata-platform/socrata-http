import sbt._

object http extends Build {
  lazy val http = Project(
    "http",
    file("."),
    settings = BuildSettings.buildSettings
  ) aggregate (httpcore)

  lazy val httpcore = Project(
    "http",
    file("httpcore"),
    settings = HttpCore.settings
  )
}
