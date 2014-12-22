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
libraryDependencies += "org.programmiersportgruppe.akre" %% "akre-client" % "0.13.0"
~~~


Setting up the Actor System
---------------------------

Akre uses a `ResilientPool` actor that manages a number of `RedisConnectionActor`s.
The `ResilientPool` needs to be configured to a special mailbox type that helps it process messages efficiently.
Since Akre uses [command pipelining] to efficiently handle many requests using a small number of connections,
we recommend using Akka's [`PinnedDispatcher`] to give a dedicated thread to the pool and to each connection actor.

[command pipelining]: http://redis.io/topics/pipelining
[`PinnedDispatcher`]: http://doc.akka.io/docs/akka/snapshot/scala/dispatchers.html#Types_of_dispatchers

Here is a minimal setup for the actor system:

~~~ {.scala}
val actorSystem = ActorSystem("akre-example", ConfigFactory.parseString(
  """
  akka {
    loglevel = ERROR
    log-dead-letters = 100
    actor.debug.unhandled = on

    actor.deployment {
      /akre-redis-pool {
        dispatcher = akre-coordinator-dispatcher
      }
      "/akre-redis-pool/*" {
        dispatcher = akre-connection-dispatcher
      }
    }
  }
  akre-coordinator-dispatcher {
    type = PinnedDispatcher
    mailbox-type = org.programmiersportgruppe.redis.client.ResilientPoolMailbox
  }
  akre-connection-dispatcher {
    type = PinnedDispatcher
  }"""
))
~~~


Using the Future Client API
---------------------------

Application code implemented as Akka actors may use the `ResilientPool` or `RedisConnectionActor` actors directly.
For use from outside the actor system, Akre provides a strongly typed API based on futures.

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


Why?
----

A teammate of mine implemented a caching solution for our system, which is written in Scala.
He was not satified with existing Scala clients for Redis, and ended up using Jedis.
We then encountered stability problems on MacOS X with versions 7 and 8 of the JDK.
A client based on Akka seamed like a reasonable thing to do.
At some point, I'll look at the other Scala clients.
Maybe I will abandon this client in favour of something more established,
but at least this will have been a good learning experience for me.
