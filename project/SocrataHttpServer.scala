import sbt._
import Keys._

import Dependencies._

object SocrataHttpServer {

  val settings: Seq[Setting[_]] = BuildSettings.projectSettings ++ Seq(
    name := "socrata-http-server",
    libraryDependencies ++=
      Seq(
        commonsIo,
        javaxServlet % "provided",
        jodaConvert,
        jodaTime,
        scalaReflect(scalaVersion.value),
        simpleArm,
        slf4jApi,
        socrataThirdpartyUtils,
        typesafeConfig,
        scalaCheck % "test"
      ),

    // macro-paradise macros
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    sourceGenerators in Compile += Def.task { genParses((sourceManaged in Compile).value, (scalaVersion in Compile).value) }
  )

  def genParse(n: Int, scalaVersion: String): String = {
    val typeVars = (0 until n).map { i => ('A' + i).toChar }
    val resultTypes = typeVars.map { t => s"Option[$t]" }
    val typeParams = typeVars.map { t => s"$t : Extractor" }
    val paramVars = (1 to n).map { i => s"r$i" }
    val resultVars = (1 to n).map { i => s"res$i" }

    val formals = paramVars.map { p => s"$p: String" }

    val lb = List.newBuilder[String]
    val parameters = if(n == 1) "parameter" else "parameters"
    val values = if(n == 1) "the parsed value" else "a tuple of the parsed values"
    lb ++= s"""/**
 * Parse $n optional query $parameters, returning either `Right` containing
 * $values, or `Left` containing a collection of errors
 * for all the unparsable parameters.  If `Left` is returned, the collection
 * will be non-empty.
 *
""".split("\n")
    lb += " * @example {{{"
    val exampleTypeUniverse = Seq("String", "Int")
    val exampleTypes = (1 to n).map { i => exampleTypeUniverse(i % exampleTypeUniverse.length) }
    val exampleParameters = (1 to n).map("param" + _)
    val exampleResults = (1 to n).map("result" + _)
    val exampleParameterStrings = exampleParameters.map("\"" + _ + "\"")
    lb += s" * req.parseQueryParametersAs[${exampleTypes.mkString(", ")}](${exampleParameterStrings.mkString(", ")}) match {"
    lb += s" *   case Right((${exampleResults.mkString(", ")})) =>"
    (exampleParameters, exampleResults).zipped.foreach { (param, result) =>
      val q = "\"" // grr scala
      lb += s" *     println($q$param = $q + $result)"
    }
    lb += " *   case Left(errors) =>"
    lb += " *     println(\"oops: \" + errors)"
    lb += " * }"
    lb += " * }}}"
    lb += " */"
    lb += s"def parseQueryParametersAs[${typeParams.mkString(", ")}](${formals.mkString(", ")}) : Either[Seq[UnparsableParam], (${resultTypes.mkString(", ")})] = {"

    (resultVars, typeVars, paramVars).zipped.foreach { (out, typ, in) =>
      lb += s"  val $out = self.parseQueryParameterAs[$typ]($in)"
    }
    val condition = resultVars.map(_ + ".isInstanceOf[UnparsableParam]").mkString(" || ")
    lb += s"  if($condition) {"
    if(scalaVersion.startsWith("2.10.")) {
      lb += s"    Left(Seq(${resultVars.mkString(", ")}).flatMap { case x: UnparsableParam => Seq(x); case _ => Nil })"
    } else {
      lb += s"    Left(Seq(${resultVars.mkString(", ")}).collect { case x: UnparsableParam => x })"
    }
    lb += "  } else {"
    val resultExtractions = resultVars.map(_ + ".get")
    lb += s"    Right((${resultExtractions.mkString(", ")}))"
    lb += "  }"
    lb += "}"

    lb.result().map("  " + _).mkString("", "\n", "\n")
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
      val functions = (1 to 22).map { i => genParse(i, scalaVersion) }.mkString("\n")
      f.write(functions)
      f.write("}\n")
    } finally {
      f.close()
    }
    Seq(outfile)
  }
}
