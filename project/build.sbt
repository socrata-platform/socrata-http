resolvers ++= Seq(
  "socrata snapshots" at "http://repository-socrata-oss.forge.cloudbees.com/snapshot",
  "socrata releases" at "http://repository-socrata-oss.forge.cloudbees.com/release"
)

addSbtPlugin("com.socrata" % "socrata-cloudbees-sbt" % "0.0.1-SNAPSHOT")
