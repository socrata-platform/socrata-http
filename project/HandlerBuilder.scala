package serverext

import java.io.{PrintWriter, FileOutputStream, OutputStreamWriter, BufferedOutputStream}
import java.nio.charset.StandardCharsets
import sbt._

object HandlerBuilder {
  def apply(dir: File): Seq[File] = {
    val filename = dir / "HandlerImpl.scala"

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

    w.println("import com.socrata.http.server.HttpRequest")

    w.println("trait HandlerImpl { this: Handler.type =>");

    for(i <- 0 to 22) {
      generateOne(w, i)
    }

    w.println("}")
  }

  def generateOne(w: PrintWriter, i: Int) {
    val tuple = (1 to i).map("T" + _).mkString("(", ",", ")")

    w.print(s"implicit def fromFunction$i[")
    w.print(((1 to (i-1)).map("T" + _ + ": FromRequestParts") ++ Some(s"T$i: FromRequest").filter(_ => i != 0) :+ "R: IntoResponse").mkString(", "))
    w.print("]: Handler[")
    w.print(tuple)
    w.print(" => R] =")
    w.print("  new Handler[")
    w.print(tuple)
    w.println(" => R] {")
    w.print("  def invoke(h: ")
    w.print(tuple)
    w.println(" => R, req: HttpRequest) = {")
    if(i > 1) {
      w.println("    val parts = RequestParts.from(req)")
    }

    generateTry(w, 1, i)

    w.println("  }")
    w.println("}")
  }

  def generateTry(w: PrintWriter, param: Int, paramCount: Int) {
    val indent = "  " * (param+1)
    w.print(indent)
    if(param == paramCount+1) {
      w.print("Accepted(IntoResponse(h(")
      w.print((1 to paramCount).map("t" + _).mkString(","))
      w.println(")))")
    } else if(param == paramCount) {
      w.println(s"FromRequest[T$param](req) match {")
      w.println(s"${indent}case Accepted(t$param) =>")
      generateTry(w, param+1, paramCount)
      w.println(s"${indent}case rejection: Rejected => rejection")
      w.println(s"$indent}")
    } else {
      w.println(s"FromRequestParts[T$param](parts) match {")
      w.println(s"${indent}case Accepted(t$param) =>")
      generateTry(w, param+1, paramCount)
      w.println(s"${indent}case rejection: Rejected => rejection")
      w.println(s"$indent}")
    }
  }
}
