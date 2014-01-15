Akre
====

Akre is a Scala Redis client implemented using Akka,
with command pipelining and convenient Actor and Future APIs.


Status
------

Consider this alpha software.
Only a handful of commands are implemented,
and Akre has not yet been used in production.
I hope that both of these will change very soon.


Why?
----

A teammate of mine implemented a caching solution for our system, which is written in Scala.
He was not satified with existing Scala clients for Redis, and ended up using Jedis.
We then encountered stability problems on MacOS X with versions 7 and 8 of the JDK.
A client based on Akka seamed like a reasonable thing to do.
At some point, I'll look at the other Scala clients.
Maybe I will abandon this client in favour of something more established,
but at least this will have been a good learning experience for me.
