package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import scala.concurrent.duration._
import akka.util.ByteString


class RedisClientTest extends ActorSystemAcceptanceTest {

  behavior of "A Redis client"


  it should "return stored keys" in {
    withRedisServer { serverAddress =>
      withActorSystem { actorSystem =>
        implicit val client = new RedisClient(actorSystem, serverAddress, 4 seconds)

        val retrieved = for {
          s <- SET(Key("A key"), ByteString("A value")).execute
          g <- GET(Key("A key")).executeString
        } yield g

        assertResult(Some("A value")) { await(retrieved) }
      }
    }
  }

}
