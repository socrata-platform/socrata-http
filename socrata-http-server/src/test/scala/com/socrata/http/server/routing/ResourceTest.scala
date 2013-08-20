package com.socrata.http.server.routing

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers

class TopLevelNonSingletonResourceWithGetAndPost extends SimpleResource {
  override def get = ???
  override def post = ???
}

object TopLevelSingletonResourceWithGetAndPost extends SimpleResource {
  override def get = ???
  override def post = ???
}

object ResourceInsideASingleton {
  object Singleton extends SimpleResource {
    override def delete = ???
  }

  class Class extends SimpleResource {
    override def patch = ???
  }
}

class ResourceInsideAClass {
  object Singleton extends SimpleResource {
    override def delete = ???
  }

  class Class extends SimpleResource {
    override def patch = ???
  }
}

trait ResourceInsideATrait {
  object Singleton extends SimpleResource {
    override def delete = ???
  }

  class Class extends SimpleResource {
    override def patch = ???
  }
}

class ResourceTest extends FunSuite with MustMatchers {
  test("Top level non-singleton resources compute their allowed-method sets correctly") {
    (new TopLevelNonSingletonResourceWithGetAndPost).allowedMethods must equal (Set("GET","POST"))
  }

  test("Top level singleton resources compute their allowed-method sets correctly") {
    TopLevelSingletonResourceWithGetAndPost.allowedMethods must equal (Set("GET","POST"))
  }

  test("Resources nested within a top-level object compute their allowed-method sets correctly") {
    ResourceInsideASingleton.Singleton.allowedMethods must equal (Set("DELETE"))
    (new ResourceInsideASingleton.Class).allowedMethods must equal (Set("PATCH"))
  }

  test("Resources nested within a top-level class compute their allowed-method sets correctly") {
    val c = new ResourceInsideAClass
    c.Singleton.allowedMethods must equal (Set("DELETE"))
    (new c.Class).allowedMethods must equal (Set("PATCH"))
  }

  test("Resources nested within a top-level trait compute their allowed-method sets correctly") {
    val c = new ResourceInsideATrait {}
    c.Singleton.allowedMethods must equal (Set("DELETE"))
    (new c.Class).allowedMethods must equal (Set("PATCH"))
  }

  test("Anonymous resources compute their allowed-method sets correctly") {
    (new SimpleResource { override def put = ??? }).allowedMethods must equal (Set("PUT"))
  }
}
