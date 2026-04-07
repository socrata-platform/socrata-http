import Dependencies._

name := "socrata-http-client-otel"

libraryDependencies ++= Seq(
  opentelemetry,
  opentelemetryIncubator
)
