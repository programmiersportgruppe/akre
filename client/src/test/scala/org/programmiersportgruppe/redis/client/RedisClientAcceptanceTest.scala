package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress
import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._
import scala.util.Failure

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands._
import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest


class RedisClientAcceptanceTest extends ActorSystemAcceptanceTest {
  import ActorSystemAcceptanceTest._

  behavior of "A Redis client"

  for ((description, address) <- Seq(
    "over IPv4" -> IPv4LoopbackAddress.map(new InetSocketAddress(_, redisServerPort)),
    "over IPv6" -> IPv6LoopbackAddress.map(new InetSocketAddress(_, redisServerPort)),
    "with unresolved InetSocketAddress" -> Some(InetSocketAddress.createUnresolved("localhost", redisServerPort))
  ))
  it should s"return stored keys when connecting $description" in {
    require(address.isDefined)
    withRedisServer(address.get) { serverAddress =>
      withActorSystem { implicit actorSystem =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected())

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
      withActorSystem { implicit actorSystem =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected())

        val deleted = for {
          s <- SET(Key("A key"), ByteString("A value")).execute
          d <- DEL(Key("A key")).executeLong
        } yield d

        assertResult(1) { await(deleted) }
      }
    }
  }


  it should "not hang forever on construction when unable to reach the server" in {
    withActorSystem { implicit actorSystem =>
      implicit val client = within(100.milliseconds) {
        new RedisClient(ConnectionPoolSettings(new InetSocketAddress("localhost", 1), 1), 3.seconds, actorSystem)
      }
      intercept[TimeoutException] {
        await(client.completeWhenConnected(timeout = 1.second))
      }

      val setCommand = SET(Key("A key"), ByteString("A value"))
      val future = setCommand.execute
      Await.ready(future, 2.seconds)

      val Some(Failure(e: RequestExecutionException)) = future.value
      e.cause shouldBe EmptyPoolException(setCommand)
    }
  }


  it should "recover from the server going down abruptly" in {
    withActorSystem { implicit actorSystem =>

      val (serverAddress, originalClient) = withRedisServer { serverAddress =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected(timeout = 1.second))

        assertResult(RSimpleString.OK) {
          await(SET(Key("A key"), ByteString(1)).execute)
        }

        (serverAddress, client)
      }
      implicit val client = originalClient

      intercept[RequestExecutionException] {
        await(SET(Key("A key"), ByteString(2)).execute)
      }

      withRedisServer(serverAddress) { _ =>
        await(client.completeWhenConnected(timeout = 1.second))

        assertResult(RSimpleString.OK) {
          await(SET(Key("A key"), ByteString(4)).execute)
        }
      }
    }
  }


  it should "recover from the server going down nicely" in {
    withActorSystem { implicit actorSystem =>
      implicit var client: RedisClient = null

      withRedisServer { serverAddress =>
        client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected(timeout = 1.second))

        await(SHUTDOWN().executeConnectionClose)
      }

      withRedisServer { serverAddress =>
        await(client.completeWhenConnected(timeout = 1.second))

        assertResult(RSimpleString.OK) {
          await(SET(Key("A key"), ByteString(4)).execute)
        }
      }
    }
  }


  it should "send connection setup commands once per client" in {
    withRedisServer { serverAddress =>
      withActorSystem { implicit actorSystem =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 3), 3.seconds, actorSystem, Seq(APPEND(Key("song"), ByteString("La"))))
        await(client.completeWhenConnected())

        eventually {
          assertResult(Some("LaLaLa")) {
            await(GET(Key("song")).executeString)
          }
        }
      }
    }
  }


  it should "return the substring of the value stored at key" in {
    withRedisServer { serverAddress =>
      withActorSystem { implicit actorSystem =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected())

        val getRange = for {
          s <- SET(Key("A key"), ByteString("This is a string")).execute
          d <- GETRANGE(Key("A key"), 0, 3).executeString
        } yield d

        assertResult(Some("This")) { await(getRange) }
      }
    }
  }

  it should "return the substring of the value stored at key for negative range" in {
    withRedisServer { serverAddress =>
      withActorSystem { implicit actorSystem =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected())

        val getRange = for {
          s <- SET(Key("A key"), ByteString("This is a string")).execute
          d <- GETRANGE(Key("A key"), -3, -1).executeString
        } yield d

        assertResult(Some("ing")) { await(getRange) }
      }
    }
  }

  it should "return the substring of the value stored at key for large ranges" in {
    withRedisServer { serverAddress =>
      withActorSystem { implicit actorSystem =>
        implicit val client = new RedisClient(ConnectionPoolSettings(serverAddress, 1), 3.seconds, actorSystem)
        await(client.completeWhenConnected())

        val getRange = for {
          s <- SET(Key("A key"), ByteString("This is a string")).execute
          d <- GETRANGE(Key("A key"), 10, 100).executeString
        } yield d

        assertResult(Some("string")) { await(getRange) }
      }
    }
  }

}
