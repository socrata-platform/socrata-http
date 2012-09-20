resolvers := Seq(
  "socrata maven" at "https://repo.socrata.com/artifactory/libs-release",
  Resolver.url("socrata ivy", new URL("https://repo.socrata.com/artifactory/ivy-libs-release"))(Resolver.ivyStylePatterns),
  "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
)

externalResolvers <<= resolvers map { rs =>
  Resolver.withDefaultResolvers(rs, mavenCentral = false)
}

ivyLoggingLevel := UpdateLogging.DownloadOnly
