import Dependencies._

name := "socrata-http-client"

libraryDependencies ++= Seq(
  apacheHttpClient exclude ("commons-logging", "commons-logging"),
  commonsIo,
  apacheHttpMime,
  jclOverSlf4j,
  simpleArm,
  slf4jApi,
  scalaTest % "test",
  scalaCheck % "test",
  slf4jSimple % "test"
)
