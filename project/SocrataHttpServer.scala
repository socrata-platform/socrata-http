import sbt._
import Keys._

import Dependencies._

object SocrataHttpServer {

  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    libraryDependencies <++=(scalaVersion) { sv =>
      Seq(
        commonsIo,
        javaxServlet % "provided",
        jodaConvert,
        jodaTime,
        scalaReflect(sv),
        simpleArm,
        slf4jApi,
        socrataThirdpartyUtils,
        typesafeConfig,
        scalaCheck % "test"
      )
    },

    // macro-paradise macros
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full),
    sourceGenerators in Compile <+= (sourceManaged in Compile, scalaVersion in Compile).map(genParses)
  )

  def genParse(n: Int, scalaVersion: String): String = {
    val typeVars = (0 until n).map { i => ('A' + i).toChar }
    val resultTypes = typeVars.map { t => s"Option[$t]" }
    val typeParams = typeVars.map { t => s"$t : Extractor" }
    val paramVars = (1 to n).map { i => s"r$i" }
    val resultVars = (1 to n).map { i => s"res$i" }

    val formals = paramVars.map { p => s"$p: String" }

    val sb = new StringBuilder
    val parameters = if(n == 1) "parameter" else "parameters"
    val values = if(n == 1) "the parsed value" else "a tuple of the parsed values"
    sb.append(s"""/**
 * Parse $n optional query $parameters, returning either `Right` containing
 * $values, or `Left` containing a collection of errors
 * for all the unparsable parameters.  If `Left` is returned, the collection
 * will be non-empty.
 *
""")
    sb.append(" * @example {{{\n")
    val exampleTypeUniverse = Seq("String", "Int")
    val exampleTypes = (1 to n).map { i => exampleTypeUniverse(i % exampleTypeUniverse.length) }
    val exampleParameters = (1 to n).map("param" + _)
    val exampleResults = (1 to n).map("result" + _)
    val exampleParameterStrings = exampleParameters.map("\"" + _ + "\"")
    sb.append(s" * req.parseQueryParametersAs[${exampleTypes.mkString(", ")}](${exampleParameterStrings.mkString(", ")}) match {\n")
    sb.append(s" *   case Right((${exampleResults.mkString(", ")})) =>\n")
    (exampleParameters, exampleResults).zipped.foreach { (param, result) =>
      val q = "\"" // grr scala
      sb.append(s" *     println($q$param = $q + $result)\n")
    }
    sb.append(" *   case Left(errors) =>\n")
    sb.append(" *     println(\"oops: \" + errors)\n")
    sb.append(" * }\n")
    sb.append(" * }}}\n")
    sb.append(" */\n")
    sb.append(s"def parseQueryParametersAs[${typeParams.mkString(", ")}](${formals.mkString(", ")}) : Either[Seq[UnparsableParam], (${resultTypes.mkString(", ")})] = {\n")

    (resultVars, typeVars, paramVars).zipped.foreach { (out, typ, in) =>
      sb.append("  val ").append(out).append(" = self.parseQueryParameterAs[").append(typ).append("](").append(in).append(")\n")
    }
    val condition = resultVars.map(_ + ".isInstanceOf[UnparsableParam]").mkString(" || ")
    sb.append(s"  if($condition) {\n")
    if(scalaVersion.startsWith("2.10.")) {
      sb.append(s"    Left(Seq(${resultVars.mkString(", ")}).flatMap { case x: UnparsableParam => Seq(x); case _ => Nil })\n")
    } else {
      sb.append(s"    Left(Seq(${resultVars.mkString(", ")}).collect { case x: UnparsableParam => x })\n")
    }
    sb.append("  } else {\n")
    val resultExtractions = resultVars.map(_ + ".get")
    sb.append(s"    Right((${resultExtractions.mkString(", ")}))\n")
    sb.append("  }\n")
    sb.append("}\n")

    sb.toString
  }

  def genParses(base: File, scalaVersion: String): Seq[File] = {
    val targetDir = base / "com" / "socrata" / "http" / "server"
    targetDir.mkdirs()
    val outfile = targetDir / "GeneratedHttpRequestApi.scala"
    val f = new java.io.FileWriter(outfile)
    try {
      f.write("""package com.socrata.http.server

import com.socrata.http.server.routing.Extractor

final class GeneratedHttpRequestApi(val `private once 2.10 is no longer a thing` : HttpRequest) extends AnyVal {
  private def self = `private once 2.10 is no longer a thing`
""")
      for(i <- 1 to 22) f.write(genParse(i, scalaVersion))
      f.write("}\n")
    } finally {
      f.close()
    }
    Seq(outfile)
  }
}
