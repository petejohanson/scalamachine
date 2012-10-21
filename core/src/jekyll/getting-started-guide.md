---
layout: gsg
title: Getting Started
---

<p class="lead">
This page will guide you through how to setup a simple Scalamachine project. After we set that
up we will explore some of the more important details of Scalamachine. 
</p>

# Setting Up 

<div class="alert">
Note: Due to limitations in previous versions of Scala, Scalamachine can only be run with Scala 2.10
and newer versions. The current version supported is 2.10.0-M7.
</div>

Whether you have an existing project or are starting out fresh, you need to add a single
to your SBT build's `libraryDependencies`. The first is the `scalamachine-core` project. The 
second is a dependency specific to what HTTP framework you wish to run Scalamachine on. For this 
guide we will use [Netty](http://netty.io). We will discuss how this in more detail
[further along](#pluggable-frameworks) in this guide.

Whether you have an existing project or are starting out fresh, you need to add a single 
dependency to your SBT build's `libraryDependencies`. The dependency you add depends on
which HTTP framework you wish to run Scalamachine on. For this guide, we will be using 
[Netty](http://netty.io). 

{% highlight scala %}
libraryDependencies ++= Seq(
  "com.stackmob" %% "scalamachine-netty" % "0.2.0-SNAPSHOT"
)
{% endhighlight %}

You can choose any supported framework and switching between frameworks
is simply a matter of changing this dependency. We will discuss this 
[further along](#pluggable-frameworks) in this guide. 

<a name="hello-scalamachine">
</a>

# Hello, Scalamachine!

Now that we have our build configured we can get to the meat of the project. Scalamachine 
projects revolve around implementations of the `scalamachine.core.Resource` trait. A `Resource` 
represents the HTTP application logic of part of your project. The simplest `Resource` we can
write, which uses all of the defaults provided by Scalamachine is:

{% highlight scala %}
import scalamachine.core.Resource
object MyFirstResource extends Resource {
  // TODO: either explain this briefly with refernce to Resource section or use ContextFreeResource
  type Context = Option[Nothing]
  def init = None
}
{% endhighlight %}

The defaults are not very interesting and we will add some code to our `Resource` soon but it is
enough for us to get going. 

Each `Resource` is associated with one or more *routes* in your application. A *route* is a 
pattern that matches a particular request's url. Routes can also contain guards: 
predicate functions applied against the entire request. 

Your application contains an ordered collection of routes, called a `RoutingTable`. 
When a request is received Scalamachine will find the route that matches your request. If one
is found the logic of the `Resource` associated with the route will be run. If a route is not
found the behavior depends on the HTTP framework you are using as well as some other details
but the typical behavior is to return a `404 Not Found` (other behaviors may include a pass-through
mechanism so Scalamachine can be plugged into existing projects without affecting the existing
request routing). 

For this example, lets serve our resource for any request with a URL ending in `/helloworld`:

{% highlight scala %}
import scalamachine.core.routing._
val routes = RoutingTable(
  pathMatching {
    "helloworld"
  } serve MyFirstResource
)
{% endhighlight %}

We will discuss routing in more detail [later](#request-routing). 

Now that we have a `Resource` and a `RoutingTable` we have everything we need to plug Scalamachine
into an HTTP framework. At this point the details are not very specific to Scalamachine and depend
on your framework of choice. For this guide we are using Netty. TODO: sentence and link to frameworks
page. 

For convenience, the `scalamachine-netty` project provides a `ChannelPipelineFactory` to constrcut
the Netty server. If you are not familiar with Netty, you can ignore this and just copy and paste
the code below into your project. 

{% highlight scala %}
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.execution.{OrderedMemoryAwareThreadPoolExecutor, ExecutionHandler}
import scalamachine.core.routing._

object ExampleServer extends App {
  val routes = RoutingTable(
    pathMatching {
      "helloworld"
    } serve MyFirstResource
  )
  
  val bootstrap = new ServerBootstrap(
    new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),Executors.newCachedThreadPool())
  )

  // Scalamachine assumes the use of an execution handler, you can create the handler
  // of your choice but it must be passed ScalamachineChannelPipelineFactory's constructor
  val execHandler = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576))
  bootstrap.setPipelineFactory(new ScalamachineChannelPipelineFactory(execHandler, routes))

  logger.info("starting Scalamachine Netty Example Server on port 8080")
  bootstrap.bind(new InetSocketAddress(8080))
}
{% endhighlight %}

This code sets up our Netty server and boots it when the scala `App` is run. We can run the 
application with sbt using `sbt run`. 

With the application running we can make a request to `/helloworld` using curl:

{% highlight bash %}

$ curl -v localhost:8080/helloworld
...
> GET /helloworld HTTP/1.1
> User-Agent: curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8r zlib/1.2.5
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Content-Type: text/html
< Connection: keep-alive
< Content-Length: 44
< 
* Connection #0 to host localhost left intact
<html><body>Hello,Scalamachine</body></html>* Closing connection #0

{% endhighlight %}

That's it! We now have a fully functioning Scalamachine applicatiomn running on top of Netty. 
In the next sections we will discuss some of the important details of Scalamachine and expand
our `Resource` implementation to be a bit more interesting. 

# Scalamachine Overview

Scalamachine is a port of [Webmachine](http://github.com/basho/webmachine) for [Erlang](http://erlang.org), 
developed at [Basho](http://basho.com). It is a framework for building HTTP applications that 
adhere strictly to HTTP semantics. Scalamachine does not attempt to be a "complete framework". 
It does not provide modules for database access or an http client implementation. It doesn't 
even implement an HTTP server. Scalamachine is a thin layer that provides a system of conventions
making it easy for you to develop your server-side application's HTTP layer while 
getting out of your way when building the rest of your application. 

## Pluggable HTTP Frameworks 

So how does Scalamachine run if it doesn't come with an implementation of an HTTP server? It relies
on your choice of several existing implementations for the JVM. Currently, only support for 
[Netty](http://netty.io) and the `javax.servlet` api exist but future work on 
[Finagle](http://twitter.github.com/finagle/) and [Lift](http://liftweb.net) are planned once 
builds for 2.10 are available. Applications built with Scalamachine can be plugged into any of 
these frameworks whether they are used for other purposes or not.

TODO: add link to frameworks page
TODO: add information on how this works? can introduce ReqRespData here
 
## HTTP Semantics 

The layer of your application that defines how the outside world communicates with it over HTTP
should be thin and not ditictate the implementation of the underlying application logic. Scalamachine
provides just enough to allow you to build APIs that conform to the HTTP specification. It does 
this by implementating [Webmachine's Flow Diagram](http://wiki.basho.com/Webmachine-Diagram.html), a
set of conventions that directly map to HTTP. 

The flow diagram, and thus Scalamachine, abstracts logic like: rejecting HTTP methods not provided 
by part of an API, content type negotiation and conditional requests. You provide details specific
to your application like how to authorize a request and what content types are provided and 
Scalamachine takes care of the rest. This is done by implementing the `Resource` trait, which we will
now discuss in more detail.
 
## Resources

Each Scalamachine application is made up of a set of `Resource`s. Each `Resource`, in turn, is 
a set of functions which operate on the `Resource`'s state, the request and response data as 
well as a `Resource` specific "context". Each function may return a value to fill in the
details Scalamachine needs, like authorization as mentioned above. The functions may also
halt processing of the resource to produce a response immediately without continuing to call
any other functions. Since `Resource`s are just this simple collection of functions, you can abstract
common functionality, like authorization, into traits and mix them into your implementations as 
needed.

The `Resource`'s state, as we just learned is, made up of two components:

<div class="well">
  <ol>
  <li>
  <strong>The Request/Response Data</strong> - Scalamachine uses a single data structure, `scalamachine.core.ReqRespData`,
  to represent both the data coming in and the data going out. Common operations performed by `Resource`
  functions are reading the value of request headers or the request body and setting response headers
  or the response body. 
  </li>  
  <li>
  <strong>Resource-specific context</strong> - Each `Resource` can also have its own context, a data structure of its choice,
  to store implemenation-specific data such as which user has been authorized or the actual data being
  accessed. To allow for each `Resource` to have its own context, each implementation must provide
  a concrete definition of the abstract type member `Context`. TODO: add note about `ContextFreeResource`.
  </li>
  </ol>
</div>  
  
In some cases, functions may have no need to operate on the above state and may simply return a value
or halt processing. In most cases, however, the state will be operated on. We will get back to that in a 
moment, lets first discuss the `Resource` functions in some more detail. 

### Resource Functions in Detail

scalamachine is opinionated framework, introduce referential transparency, etc
return types, monad, transformer stack
show how content types provided works
start customizing the resource a bit
the above may need a new section

## Request Routing

Guards can be used to place conditions on which requests are routed to which resources based on
 headers and other details of the request.


# Next Steps



