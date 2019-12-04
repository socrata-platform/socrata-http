resolvers ++= Seq(
  "socrata releases" at "https://repo.socrata.com/libs-release"
)

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.1")
