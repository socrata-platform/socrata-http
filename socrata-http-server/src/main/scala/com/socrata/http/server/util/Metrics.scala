package com.socrata.http.server.util

import com.codahale.metrics.MetricRegistry
import nl.grons.metrics.scala.InstrumentedBuilder

/*
 * Initializes a MetricRegistry for the HTTP server to record all kinds of metrics
 * Only one is needed for the whole application, and this is thread safe.
 */
object Metrics {
  val metricsRegistry = new MetricRegistry
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
  val metrics = Metrics.metricsRegistry
}