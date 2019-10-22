resolvers ++= Seq(
  "socrata releases" at "https://repo.socrata.com/libs-release",
  Resolver.url("typesafe sbt-plugins", url("https://dl.bintray.com/typesafe/sbt-plugins"))(Resolver.ivyStylePatterns)
)

libraryDependencies ++= Seq(
  "org.scalacheck" %% "scalacheck" % "1.11.3" % Test,
  "org.scala-lang" % "scala-library" % "2.10.6",
  "org.apache.httpcomponents" % "httpclient" % "4.3.3"
)

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.11")
