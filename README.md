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


Why?
----

A teammate of mine implemented a caching solution for our system, which is written in Scala.
He was not satified with existing Scala clients for Redis, and ended up using Jedis.
We then encountered stability problems on MacOS X with versions 7 and 8 of the JDK.
A client based on Akka seamed like a reasonable thing to do.
At some point, I'll look at the other Scala clients.
Maybe I will abandon this client in favour of something more established,
but at least this will have been a good learning experience for me.
