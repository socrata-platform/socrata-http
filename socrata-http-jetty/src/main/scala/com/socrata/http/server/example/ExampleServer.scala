package com.socrata.http.server.example

import com.socrata.http.server.SocrataServerJetty

/**
 * A minimal socrata-http app
 *
 * All it does is sleep and print some debugging stuff.
 * it also sets up a really shallow queue for testing backpressure.
 *
 * For example, to test it, do
 *   seq 20 | parallel -n0 curl localhost:23456/v1/widgets
 *
 * You will see that most of the curl requests fail with an empty reply, only a few succeed.
 */
object ExampleServer extends App {
  val exampleService = new ExampleService(250)   // Each post delays 250 ms
  val router = new ExampleRouter(exampleService.exampleRoutes)
  val handler = router.route _
  // use a minimal length queue for testing purposes
  // The 10th request or so should start getting rejected.
  val poolOptions = SocrataServerJetty.Pool.defaultOptions
                      .withMinThreads(3)
                      .withMaxThreads(6)
                      .withQueueLength(3)
  val server = new SocrataServerJetty(handler,
                                      SocrataServerJetty.defaultOptions
                                        .withPort(23456)
                                        .withPoolOptions(poolOptions))
  server.run()
}