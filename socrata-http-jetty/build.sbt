import Dependencies._

name := "socrata-http-jetty"

libraryDependencies ++= Seq(
  jakartaServlet % Provided,
  socrataUtils,
  jettyJmx,
  jettyServer,
  jettyServlet,
  jettyServlets,
  slf4jApi
)
