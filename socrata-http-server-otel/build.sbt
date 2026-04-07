import Dependencies._

name := "socrata-http-server-otel"

libraryDependencies ++= Seq(
  opentelemetry,
  opentelemetryIncubator
)
