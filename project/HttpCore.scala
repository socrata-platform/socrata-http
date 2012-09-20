import sbt._
import Keys._

import com.socrata.socratasbt.SocrataSbt._
import SocrataSbtKeys._

object HttpCore {
  lazy val settings: Seq[Setting[_]] = BuildSettings.buildSettings ++ socrataProjectSettings() ++ Seq(
    libraryDependencies <++= (slf4jVersion, scalaVersion) { libraries(_)(_) }
  )

  def libraries(slf4jVersion: String)(implicit scalaVersion: String) = {
    val deps = new Dependencies(slf4jVersion)
    import deps._
    Seq(
      jettyJmx,
      jettyServer,
      jettyServlet,
      socrataUtils
    )
  }
}
