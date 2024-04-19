package com.socrata.http.server

@deprecated(message="use SocrataServerJetty.Gzip.Options instead", since = "2.1.0")
case class GzipParameters(excludeUserAgent: String => Boolean = Set.empty,
                          excludeMimeTypes: String => Boolean = Set.empty,
                          bufferSize: Int = 8192,
                          minGzipSize: Int = 256)
{
  def toOptions = SocrataServerJetty.Gzip.defaultOptions.
    withBufferSize(bufferSize).
    withMinGzipSize(minGzipSize).
    withExcludedMimeTypes(toSet("excludeMimeTypes", excludeMimeTypes))

  private def toSet(name: String, f: String => Boolean): Set[String] = {
    if(f.isInstanceOf[Set[_]]) f.asInstanceOf[Set[String]]
    else {
      val logger = org.slf4j.LoggerFactory.getLogger(classOf[GzipParameters])
      logger.warn("Cannot convert GzipParameters option {} to Set; using the empty set", name)
      Set.empty
    }
  }
}
