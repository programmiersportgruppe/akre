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

Inlcude the following line into your build.sbt to get started:


~~~ {.scala}
libraryDependencies += "org.programmiersportgruppe.akre" %% "akre-client" % "0.12.0"
~~~

Setting up the Actor System
---------------------------

A minimal setup for the actor system:

~~~ {.scala}
val actorSystem = ActorSystem("akre-example", ConfigFactory.parseString(
    s"""
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

Futures offer an elegant way of dealing with asynchronous requests.
The future API is arguably the easiest way to use akre.

First we create a client:

~~~ {.scala}
val client = new RedisClient(
    actorSystem,
    InetSocketAddress.createUnresolved("127.0.0.1", 6379),
    Timeout(1000, MILLISECONDS),
    Timeout(1000, MILLISECONDS),
    1
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

Don't forget to shut down the actor system:

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
