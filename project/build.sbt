resolvers ++= Seq(
  "socrata releases" at "https://repo.socrata.com.com/libs-release"
)

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.11")
