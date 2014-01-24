package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import scala.concurrent.duration._
import akka.util.ByteString


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

}
