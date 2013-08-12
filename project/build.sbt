resolvers += "DiversIT repo" at "http://repository-diversit.forge.cloudbees.com/release"

addSbtPlugin("eu.diversit.sbt.plugin" % "webdav4sbt" % "1.3")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.5")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")
