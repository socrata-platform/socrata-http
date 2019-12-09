import Dependencies._

name := "socrata-http-jetty"

libraryDependencies ++= Seq(
  socrataUtils,
  jettyJmx,
  jettyServer,
  jettyServlet,
  jettyServlets,
  slf4jApi
)
