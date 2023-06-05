package serverext

import java.io.{PrintWriter, FileOutputStream, OutputStreamWriter, BufferedOutputStream}
import java.nio.charset.StandardCharsets
import sbt._

object IntoResponseBuilder {
  def apply(dir: File): Seq[File] = {
    val filename = dir / "IntoResponseImpl.scala"

    dir.mkdirs()
    val fos = new FileOutputStream(filename)
    try {
      val w = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(fos), StandardCharsets.UTF_8))
      generate(w)
      w.close()
    } finally {
      fos.close()
    }

    Seq(filename)
  }

  def generate(w: PrintWriter) {
    w.println("package com.socrata.http.server.ext")
    w.println("package `-impl`")

    w.println("import com.socrata.http.server.responses._")
    w.println("import com.socrata.http.server.implicits._")

    w.println("trait IntoResponseImpl { this: IntoResponse.type =>");

    for(i <- 1 until 22) {
      generateOne(w, i)
    }
    for(i <- 1 until 22) {
      generateStatusOne(w, i)
    }

    w.println("}")
  }

  def generateOne(w: PrintWriter, i: Int) {
    val tuple = ((1 to i).map("T" + _) :+ "R").mkString("(", ",", ")")

    w.print(s"implicit def t$i[")
    w.print((1 to i).map("T" + _ + ": IntoResponseParts").mkString(", "))
    w.print(", R: IntoResponse]: IntoResponse[")
    w.print(tuple)
    w.print("] =")
    w.print("  new IntoResponse[")
    w.print(tuple)
    w.println("] {")
    w.print("    def intoResponse(tuple: ")
    w.print(tuple)
    w.println(") = {")
    w.println("      var parts = ResponseParts.default")
    for(n <- 1 to i) {
      w.println(s"      parts = IntoResponseParts(tuple._$n, parts)")
    }
    w.println(s"      parts ~> IntoResponse(tuple._${i+1})")
    w.println("    }")
    w.println("  }")
  }

  def generateStatusOne(w: PrintWriter, i: Int) {
    val tuple = ((2 to i).map("T" + _) :+ "R").mkString("(StatusCode,", ",", ")")

    w.print(s"implicit def s$i[")
    w.print(((2 to i).map("T" + _ + ": IntoResponseParts") :+ "R: IntoResponse").mkString(", "))
    w.print("]: IntoResponse[")
    w.print(tuple)
    w.print("] =")
    w.print("  new IntoResponse[")
    w.print(tuple)
    w.println("] {")
    w.print("    def intoResponse(tuple: ")
    w.print(tuple)
    w.println(") = {")
    w.println("      var parts = ResponseParts.default")
    for(n <- 2 to i) {
      w.println(s"      parts = IntoResponseParts(tuple._$n, parts)")
    }
    w.println(s"      Status(tuple._1.code) ~> (parts ~> IntoResponse(tuple._${i+1}))")
    w.println("    }")
    w.println("  }")
  }
}
