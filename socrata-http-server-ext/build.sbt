libraryDependencies ++= Seq(
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"
)

Compile / sourceGenerators += Def.task { serverext.IntoResponseBuilder((Compile / sourceManaged).value) }

Compile / sourceGenerators += Def.task { serverext.HandlerBuilder((Compile / sourceManaged).value) }
