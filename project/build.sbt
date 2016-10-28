resolvers ++= Seq(
  "socrata releases" at "https://repo.socrata.com/libs-release"
)

addSbtPlugin("com.socrata" % "socrata-cloudbees-sbt" % "1.4.1")
