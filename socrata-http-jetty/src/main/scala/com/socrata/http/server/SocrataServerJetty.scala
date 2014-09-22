package com.socrata.http.server

import org.eclipse.jetty.server.Handler
import scala.concurrent.duration._

class SocrataServerJetty(handler: HttpService, options: SocrataServerJetty.Options) extends
  AbstractSocrataServerJetty(new FunctionHandler(handler), options)
{
  /**
   * @param handler Service used to handle requests.
   * @param onStop Called when a TERM or INT signal is received, after shutting down the listening socket but before
   *               waiting for pending requests to terminate.
   * @param port Port to listen on.  Pass "0" to choose a port at random.
   * @param broker A system to inform (de)readiness.
   * @param deregisterWaitMS Amount of time to give the broker before shutting down the listening socket.
   * @param gracefulShutdownTimeoutMS Maximum amount of time to wait for in-progress requests to stop.
   * @param onFatalException A function to handle fatal exceptions
   * @param gzipParameters GZIP decoding parameters
   * @param extraHandlers A list of functions that take a base Jetty Handler and return a wrapped Handler.
   *                      An example of where this is useful is for instrumenting with MetricsHandler.
   */
  @deprecated("Use SocrataServerJetty.Options instead", since="2.1.0")
  def this(handler: HttpService,
           onStop: () => Unit = AbstractSocrataServerJetty.noop,
           port: Int = 2401,
           broker: ServerBroker = ServerBroker.Noop,
           deregisterWaitMS: Int = 5000,
           gracefulShutdownTimeoutMS: Int = 60*60*1000,
           onFatalException: Throwable => Unit = AbstractSocrataServerJetty.shutDownJVM,
           gzipParameters: Option[GzipParameters] = Some(GzipParameters()),
           extraHandlers: List[Handler => Handler] = Nil) =
    this(handler, SocrataServerJetty.defaultOptions.
           withOnStop(onStop).
           withPort(port).
           withBroker(broker).
           withDeregisterWait(deregisterWaitMS.millis).
           withGracefulShutdownTimeout(gracefulShutdownTimeoutMS.millis).
           withOnFatalException(onFatalException).
           withGzipOptions(gzipParameters.map(_.toOptions)).
           withExtraHandlers(extraHandlers))
}

object SocrataServerJetty {
  type Options = AbstractSocrataServerJetty.Options
  val defaultOptions = AbstractSocrataServerJetty.defaultOptions
  val Gzip = AbstractSocrataServerJetty.Gzip
}
