resolvers ++= Seq(
  "socrata releases" at "https://repo.socrata.com/libs-release",
  Resolver.url("typesafe sbt-plugins", url("https://dl.bintray.com/typesafe/sbt-plugins"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.11")
