package com.socrata.http.server.util

import com.codahale.metrics.{MetricRegistry, Slf4jReporter}
import java.util.concurrent.TimeUnit
import nl.grons.metrics.scala.InstrumentedBuilder
import org.slf4j.LoggerFactory

/*
 * Initializes a MetricRegistry for the HTTP server to record all kinds of metrics
 * Only one is needed for the whole application, and this is thread safe.
 */
object Metrics {
  val metricsRegistry = new MetricRegistry

  lazy val reporter = Slf4jReporter.forRegistry(metricsRegistry)
                                   .outputTo(LoggerFactory.getLogger("socrata-metrics"))
                                   .convertRatesTo(TimeUnit.SECONDS)
                                   .convertDurationsTo(TimeUnit.MILLISECONDS)
                                   .build()

  /**
   * Start logging all metrics at a regular interval
   */
  def startMetricsLogging() = reporter.start(1, TimeUnit.MINUTES)
}

/**
 * Mix this into your classes etc. to get easy access to metrics.  Example:
 * {{{
 *   class RowDAO(db: Database) extends Metrics {
 *     val dbTimer = metrics.timer("db-access-latency")
 *     dbTimer.time {
         db.runQuery(....)
 *     }
 *   }
 * }}}
 * See https://github.com/erikvanoosten/metrics-scala/blob/master/docs/Manual.md for
 * more usage info, such as how to override metrics name, etc.
 */
trait Metrics extends InstrumentedBuilder {
  val metricsRegistry = Metrics.metricsRegistry
}