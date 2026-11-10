import Dependencies._

name := "socrata-http-server-openapi"

libraryDependencies ++= Seq(
  rojomaJsonV3,
  scalaTest % "test",
  snakeYaml % "test"
)
