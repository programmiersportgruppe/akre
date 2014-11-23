package org.programmiersportgruppe.redis.client

import scala.concurrent.{Await, TimeoutException}
import scala.concurrent.duration._
import scala.util.Failure

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands._
import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest


class RedisClientAcceptanceTest extends ActorSystemAcceptanceTest {

  behavior of "A Redis client"

  for ((protocol, addressLength) <- Seq(
    "IPv4" -> 4,
    "IPv6" -> 16
  ))
  it should s"return stored keys over $protocol" in {
    import ActorSystemAcceptanceTest.LoopbackAddresses
    val loopbackAddress = LoopbackAddresses.find(_.getAddress.length == addressLength)
    assume(loopbackAddress.isDefined,
      s"Couldn't find $protocol address ($addressLength bytes) among ${LoopbackAddresses.size} loopback addresse(s):" +
        LoopbackAddresses.map(_ + "\n    ").mkString
    )
    withRedisServer(loopbackAddress.get) { serverAddress =>
      withActorSystem { actorSystem =>
        implicit val client = new RedisClient(actorSystem, serverAddress.getHostName, serverAddress.getPort, 3.seconds, 3.seconds, 1)
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
        implicit val client = new RedisClient(actorSystem, serverAddress.getHostName, serverAddress.getPort, 3.seconds, 3.seconds, 1)
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
        new RedisClient(actorSystem, "localhost", 1, 1.second, 3.seconds, 1)
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
        client = new RedisClient(actorSystem, serverAddress.getHostName, serverAddress.getPort, 1.second, 3.seconds, 1)
        client.waitUntilConnected(1.second)

        assertResult(RSimpleString.OK) {
          await(SET(Key("A key"), ByteString(1)).execute)
        }
      }

      intercept[RequestExecutionException] {
        await(SET(Key("A key"), ByteString(2)).execute)
      }

      withRedisServer { serverAddress =>
        client.waitUntilConnected(1.second)

        assertResult(RSimpleString.OK) {
          await(SET(Key("A key"), ByteString(4)).execute)
        }
      }
    }
  }


  it should "recover from the server going down nicely" in {
    withActorSystem { actorSystem =>
      implicit var client: RedisClient = null

      withRedisServer { serverAddress =>
        client = new RedisClient(actorSystem, serverAddress.getHostName, serverAddress.getPort, 50.milliseconds, 3.seconds, 1)
        client.waitUntilConnected(1.second)

        await(SHUTDOWN().executeConnectionClose)
      }

      withRedisServer { serverAddress =>
        client.waitUntilConnected(200.milliseconds)

        assertResult(RSimpleString.OK) {
          await(SET(Key("A key"), ByteString(4)).execute)
        }
      }
    }
  }


  it should "send connection setup commands once per client" in {
    withRedisServer { serverAddress =>
      withActorSystem { actorSystem =>
        implicit val client = new RedisClient(actorSystem, serverAddress.getHostName, serverAddress.getPort, 3.seconds, 3.seconds, 3, Seq(APPEND(Key("song"), ByteString("La"))))
        client.waitUntilConnected(5.seconds)

        eventually {
          assertResult(Some("LaLaLa")) {
            await(GET(Key("song")).executeString)
          }
        }
      }
    }
  }

}
