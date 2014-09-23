Socrata-HTTP is a toolkit for HTTP client access and building rack-style servers.  The server is Jetty-based, but not Servlet-based, though it does use `HttpServletRequest` and `HttpServletResponse`.

For an example, see [TeaService](https://github.com/socrata/socrata-httparty/blob/master/src/main/scala/com/socrata/teaparty/services/TeaService.scala), [TeaRouter](https://github.com/socrata/socrata-httparty/blob/master/src/main/scala/com/socrata/teaparty/handlers/TeaRouter.scala) and [TeaParty](https://github.com/socrata/socrata-httparty/blob/master/src/main/scala/com/socrata/teaparty/TeaParty.scala).

## Features

* Zookeeper-based service discovery and registration
* Graceful termination of requests at exit