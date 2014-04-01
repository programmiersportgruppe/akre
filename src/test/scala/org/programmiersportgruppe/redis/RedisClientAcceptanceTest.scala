package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import scala.concurrent.duration._
import akka.util.ByteString
import java.net.InetSocketAddress
import java.util.Date
import scala.concurrent.{TimeoutException, Await}
import akka.pattern.AskTimeoutException
import scala.util.Failure


class RedisClientAcceptanceTest extends ActorSystemAcceptanceTest {

  behavior of "A Redis client"


  it should "return stored keys" in {
    withRedisServer { serverAddress =>
      withActorSystem { actorSystem =>
        implicit val client = new RedisClient(actorSystem, serverAddress, 3.seconds, 3.seconds, 1)
        client.waitUntilConnected(5.seconds)

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
        implicit val client = new RedisClient(actorSystem, serverAddress, 3.seconds, 3.seconds, 1)
        client.waitUntilConnected(5.seconds)

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
      implicit val client = within(100.milliseconds) {
        new RedisClient(actorSystem, new InetSocketAddress("localhost", 1), 1.second, 3.seconds, 1)
      }
      intercept[TimeoutException] {
        client.waitUntilConnected(1.second)
      }

      val setCommand = SET(Key("A key"), ByteString("A value"))
      val future = setCommand.execute
      Await.ready(future, 2.seconds)

      val Some(Failure(e: RequestExecutionException)) = future.value
      e.cause shouldBe EmptyPoolException(setCommand)
    }
  }


  it should "recover from the server going down abruptly" in {
    withActorSystem { actorSystem =>
      implicit var client: RedisClient = null

      withRedisServer { serverAddress =>
        client = new RedisClient(actorSystem, serverAddress, 1.second, 3.seconds, 1)
        client.waitUntilConnected(1.second)

        assertResult(StatusReply("OK")) {
          await(SET(Key("A key"), ByteString(1)).execute)
        }
      }

      intercept[RequestExecutionException] {
        await(SET(Key("A key"), ByteString(2)).execute)
      }

      withRedisServer { serverAddress =>
        client.waitUntilConnected(1.second)

        assertResult(StatusReply("OK")) {
          await(SET(Key("A key"), ByteString(4)).execute)
        }
      }
    }
  }


  it should "recover from the server going down nicely" in {
    withActorSystem { actorSystem =>
      implicit var client: RedisClient = null

      withRedisServer { serverAddress =>
        client = new RedisClient(actorSystem, serverAddress, 50.milliseconds, 3.seconds, 1)
        client.waitUntilConnected(1.second)

        intercept[RequestExecutionException] {
          await(SHUTDOWN().executeConnectionClose)
        }
      }

      withRedisServer { serverAddress =>
        client.waitUntilConnected(50.milliseconds)

        assertResult(StatusReply("OK")) {
          await(SET(Key("A key"), ByteString(4)).execute)
        }
      }
    }
  }

}
