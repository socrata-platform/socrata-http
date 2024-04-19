package com.socrata.http.server

import scala.collection.JavaConverters._
import jakarta.servlet.DispatcherType
import java.util.{EventListener, EnumSet}
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.servlet.ServletContextHandler

import scala.concurrent.duration.{FiniteDuration, Duration}

class SocrataServerJettyServlet(options: SocrataServerJettyServlet.Options) extends
  AbstractSocrataServerJetty(options.cobbleTogetherHandler, options)

object SocrataServerJettyServlet {
  import scala.language.existentials
  case class ServletSpec(servlet: Class[_ <: jakarta.servlet.Servlet], pathSpec: String)
  case class FilterSpec(filter: Class[_ <: jakarta.servlet.Filter], pathSpec: String, dispatches: Set[DispatcherType])

  abstract class Options extends AbstractSocrataServerJetty.Options {
    type OptT <: Options

    private[SocrataServerJettyServlet] def cobbleTogetherHandler: Handler = {
      val handler = new ServletContextHandler
      listeners.foreach(handler.addEventListener)
      for(servlet <- servlets) {
        handler.addServlet(servlet.servlet, servlet.pathSpec)
      }
      for(filter <- filters) {
        val d = EnumSet.copyOf(filter.dispatches.asJava)
        handler.addFilter(filter.filter, filter.pathSpec, d)
      }
      handler
    }

    val listeners: Seq[EventListener]
    def withListeners(ls: Seq[EventListener]): OptT

    val servlets: Seq[ServletSpec]
    def withServlets(ss: Seq[ServletSpec]): OptT

    val filters: Seq[FilterSpec]
    def withFilters(fs: Seq[FilterSpec]): OptT
  }

  private case class OptionsImpl(
    listeners: Seq[EventListener] = Nil,
    servlets: Seq[ServletSpec] = Nil,
    filters: Seq[FilterSpec] = Nil,
    deregisterWait: FiniteDuration = AbstractSocrataServerJetty.defaultOptions.deregisterWait,
    gzipOptions: Option[Gzip.Options] = AbstractSocrataServerJetty.defaultOptions.gzipOptions,
    broker: ServerBroker = AbstractSocrataServerJetty.defaultOptions.broker,
    onFatalException: (Throwable) => Unit = AbstractSocrataServerJetty.defaultOptions.onFatalException,
    gracefulShutdownTimeout: Duration = AbstractSocrataServerJetty.defaultOptions.gracefulShutdownTimeout,
    onStop: () => Unit = AbstractSocrataServerJetty.defaultOptions.onStop,
    port: Int = AbstractSocrataServerJetty.defaultOptions.port,
    hookSignals: Boolean = AbstractSocrataServerJetty.defaultOptions.hookSignals,
    extraHandlers: List[Handler => Handler] = AbstractSocrataServerJetty.defaultOptions.extraHandlers,
    errorHandler: Option[HttpRequest => HttpResponse] = None,
    poolOptions: Pool.Options = AbstractSocrataServerJetty.defaultOptions.poolOptions,
    requestHeaderSize: Int = 8192
  ) extends Options {
    type OptT = OptionsImpl

    override def withServlets(ss: Seq[ServletSpec]) = copy(servlets = ss)
    override def withFilters(fs: Seq[FilterSpec]) = copy(filters = fs)
    override def withListeners(ls: Seq[EventListener]) = copy(listeners = ls)
    override def withDeregisterWait(dw: FiniteDuration) = copy(deregisterWait = dw)
    override def withOnStop(callback: () => Unit) = copy(onStop = callback)
    override def withPort(p: Int) = copy(port = p)
    override def withBroker(b: ServerBroker) = copy(broker = b)
    override def withOnFatalException(callback: Throwable => Unit) = copy(onFatalException = callback)
    override def withGracefulShutdownTimeout(gst: Duration) = copy(gracefulShutdownTimeout = gst)
    override def withGzipOptions(gzo: Option[Gzip.Options]) = copy(gzipOptions = gzo)
    override def withHookSignals(enabled: Boolean) = copy(hookSignals = enabled)
    override def withExtraHandlers(h: List[Handler => Handler]) = copy(extraHandlers = h)
    override def withErrorHandler(h: Option[HttpRequest => HttpResponse]) = copy(errorHandler = h)
    override def withPoolOptions(pOpt: Pool.Options) = copy(poolOptions = pOpt)
    override def withIdleTimeout(it: Int) = this
    override def withRequestHeaderSize(size: Int) = copy(requestHeaderSize = size)
  }

  val defaultOptions: Options = OptionsImpl()
  val Gzip = AbstractSocrataServerJetty.Gzip
  val Pool = AbstractSocrataServerJetty.Pool
}
