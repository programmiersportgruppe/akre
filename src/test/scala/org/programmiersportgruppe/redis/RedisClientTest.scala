package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import scala.concurrent.{Awaitable, Await}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import java.net.InetSocketAddress
import akka.util.ByteString
import com.typesafe.config.{ConfigFactory, Config}


class RedisClientTest extends ActorSystemAcceptanceTest {

  behavior of "A Redis client"

  val serverPort = 4321
  val serverAddress = new InetSocketAddress("localhost", serverPort)
  val redisServerProcess = sys.process.Process(Seq("/usr/local/bin/redis-server"
    , "--port", serverPort.toString
    , "--bind", "localhost"
    , "--save", ""  // disable saving state to disk
  )).run()
  def actorSystem = ActorSystem("Test-actor-system-for-" + getClass.getSimpleName, ConfigFactory.parseString(
    """
      akka {
        loglevel = DEBUG
        log-dead-letters = 10
      }
    """
  ))
  def await(awaitable: Awaitable[_]) = Await.result(awaitable, 5 seconds)

  implicit def string2byteString(string: String): ByteString = ByteString(string)
  import scala.concurrent.ExecutionContext.Implicits.global

  it should "return stored keys" in {
    implicit val client = new RedisClient(actorSystem, serverAddress, 4 seconds)

    val retrieved = for {
      s <- SET(Key("A key"), "A value").execute
      g <- GET(Key("A key")).executeString
    } yield g
//
    assertResult(Some("A value")) { await(retrieved) }
  }

}
