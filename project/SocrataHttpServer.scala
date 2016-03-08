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
    sourceGenerators in Compile <+= (sourceManaged in Compile).map(genParses)
  )

  def genParse(n: Int): String = {
    val typeVars = (0 until n).map { i => ('A' + i).toChar }.toIndexedSeq
    val resultTypes = typeVars.map("Option[" + _ + "]")
    val typeParams = typeVars.map(_ + " : Extractor")
    val paramVars = (1 to n).map("r" + _).toIndexedSeq
    val resultVars = (1 to n).map("res" + _).toIndexedSeq

    val formals = paramVars.map { p => p + ": String" }

    val sb = new StringBuilder
    sb.append("def parseQueryParametersAs[").append(typeParams.mkString(", ")).append("](").append(formals.mkString(",")).append(") : Either[Seq[UnparsableParam], (").append(resultTypes.mkString(", ")).append(")] = {\n")

    (resultVars, typeVars, paramVars).zipped.foreach { (out, typ, in) =>
      sb.append("  val ").append(out).append(" = self.parseQueryParameterAs[").append(typ).append("](").append(in).append(")\n")
    }
    val condition = resultVars.map(_ + ".isInstanceOf[UnparsableParam]").mkString(" || ")
    sb.append("  if(").append(condition).append(") {\n")
    sb.append("    Left(Seq(").append(resultVars.mkString(", ")).append(").collect { case x: UnparsableParam => x })\n")
    sb.append("  } else {\n")
    sb.append("    Right((").append(resultVars.map(_ + ".get").mkString(", ")).append("))\n")
    sb.append("  }\n")
    sb.append("}\n")

    sb.toString
  }

  def genParses(base: File): Seq[File] = {
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
      for(i <- 1 to 22) f.write(genParse(i))
      f.write("}\n")
    } finally {
      f.close()
    }
    Seq(outfile)
  }
}
