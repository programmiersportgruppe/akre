Akre
====

Akre is a Scala Redis client implemented using Akka,
with command pipelining and convenient Actor and Future APIs.


Status
------

[![Build Status](https://travis-ci.org/programmiersportgruppe/akre.svg?branch=master)](https://travis-ci.org/programmiersportgruppe/akre)

Consider this beta software.
Akre is being used in production,
but currently only a handful of commands have strongly-typed representations,
and the interface is subject to change.


Getting Started
---------------

Include the following line in your `build.sbt` to get started:

~~~ {.scala}
libraryDependencies += "org.programmiersportgruppe.akre" %% "akre-client" % "0.19.0"
~~~

The easiest way to use Akre is through the `AkreClient` `Future`-based API described below.
If you're interested in using Akre from other Akka actors,
you can either directly use `RedisConnectionActor`, or use it through a `Resilient Pool` like `AkreClient` does.


Using the `AkreClient` API and Futures
--------------------------------------

Futures offer an elegant way of dealing with asynchronous requests,
and the future API is arguably the easiest way to use Akre.

First we create a client:

~~~ {.scala}
val client = new RedisClient(
    actorRefFactory     = actorSystem,
    serverAddress       = InetSocketAddress.createUnresolved("127.0.0.1", 6379),
    connectTimeout      = Timeout(1000, MILLISECONDS),
    requestTimeout      = Timeout(1000, MILLISECONDS),
    numberOfConnections = 1
)
~~~

Then we wait for the client to be ready:

~~~ {.scala}
client.waitUntilConnected(5.seconds)
~~~

Now we can send commands using the execute method:

~~~ {.scala}
val response: Future[RSuccessValue] = client.execute(SET(Key("hello"), "cruel world"))

println(Await.result(response, 5.seconds))
~~~

We can also call execute methods on the commands themselves,
which help guide us to more specific, command-appropriate return types:

~~~ {.scala}
val value: Future[Option[ByteString]] = GET(Key("hello")).executeByteString(client)

val utf8Decoded: Future[Option[String]] = GET(Key("hello")).executeString(client)
~~~

When you're done with client,
don't forget to shut down the actor system if you're not using it for something else:

~~~ {.scala}
actorSystem.shutdown()
~~~


Akre Development
----------------

Pull requests are welcome.

You should run

~~~ {.sh}
sbt +test +doc
~~~

prior to pushing to validate your changes.

The release process is documented in [RELEASING.md](RELEASING.md).


Why?
----

A teammate of mine implemented a caching solution for our system, which is written in Scala.
He was not satified with existing Scala clients for Redis, and ended up using Jedis.
We then encountered stability problems on MacOS X with versions 7 and 8 of the JDK.
A client based on Akka seamed like a reasonable thing to do.
At some point, I'll look at the other Scala clients.
Maybe I will abandon this client in favour of something more established,
but at least this will have been a good learning experience for me.
