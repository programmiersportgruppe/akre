Programmiersportgruppe Scala Redis Client
=========================================

A Scala Redis client implemented using Akka,
with command pipelining and convenient Actor and Future APIs.


Why?
----

A teammate of mine implemented a caching solution for our system, which is written in Scala.
He was not satified with existing Scala clients for Redis, and ended up using Jedis.
We then encountered stability problems on MacOS X with versions 7 and 8 of the JDK.
A client based on Akka seamed like a reasonable thing to do.
At some point, I'll look at the other Scala clients.
