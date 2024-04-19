import Dependencies.jakartaServlet

name := "socrata-http-server-ext"

libraryDependencies ++= Seq(
  jakartaServlet % Provided
)

Compile / sourceGenerators += Def.task { serverext.IntoResponseBuilder((Compile / sourceManaged).value) }

Compile / sourceGenerators += Def.task { serverext.HandlerBuilder((Compile / sourceManaged).value) }
