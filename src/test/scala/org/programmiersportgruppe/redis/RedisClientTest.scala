package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import scala.concurrent.duration._
import akka.util.ByteString
import java.net.InetSocketAddress
import java.util.Date
import scala.concurrent.Await


class RedisClientTest extends ActorSystemAcceptanceTest {

  behavior of "A Redis client"


  it should "return stored keys" in {
    withRedisServer { serverAddress =>
      withActorSystem { actorSystem =>
        implicit val client = new RedisClient(actorSystem, serverAddress, 4.seconds, 3)

        val retrieved = for {
          s <- SET(Key("A key"), ByteString("A value")).execute
          g <- GET(Key("A key")).executeString
        } yield g

        assertResult(Some("A value")) { await(retrieved) }
      }
    }
  }


  it should "delete stored keys" in {
    withRedisServer { serverAddress =>
      withActorSystem { actorSystem =>
        implicit val client = new RedisClient(actorSystem, serverAddress, 4.seconds, 3)

        val deleted = for {
          s <- SET(Key("A key"), ByteString("A value")).execute
          d <- DEL(Key("A key")).executeLong
        } yield d

        assertResult(1) { await(deleted) }
      }
    }
  }


  it should "not hang forever on construction when unable to reach the server" in {
    withActorSystem { actorSystem =>
      implicit val client = new RedisClient(actorSystem, new InetSocketAddress("localhost", 1), 4.seconds, 3)

      val deadline = 7.seconds.fromNow
      val set = for {
        s <- SET(Key("A key"), ByteString("A value")).execute
      } yield s

      Await.ready(set, 8.seconds)
      assert(deadline.hasTimeLeft())
    }
  }

}
