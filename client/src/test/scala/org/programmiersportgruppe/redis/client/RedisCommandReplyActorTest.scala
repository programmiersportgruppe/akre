package org.programmiersportgruppe.redis.client

import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.{ GET, SET, SHUTDOWN }
import org.programmiersportgruppe.redis.test.{ ActorSystemAcceptanceTest, ProxyingParent }

class RedisCommandReplyActorTest extends ActorSystemAcceptanceTest {

  behavior of "a connection to the Redis server"

  it should "persist keys to the database" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val testKit = new TestKit(system)

        val redis = ProxyingParent(RedisCommandReplyActor.props(address, messageToParentOnConnected = Some("ready")), testKit.testActor, "redis-actor")
        testKit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        assertResult(set -> RSimpleString.OK) {
          await(redis ? set)
        }

        val get = GET(Key("foo"))
        assertResult(get -> RBulkString("bar")) {
          await(redis ? get)
        }

        testKit.watch(redis)
        redis ! SHUTDOWN()
        testKit.expectTerminated(redis)
      }
    }
  }

  it should "handle receiving multiple commands at once" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val testKit = new TestKit(system)

        val redis = ProxyingParent(RedisCommandReplyActor.props(address, messageToParentOnConnected = Some("ready")), testKit.testActor, "redis-actor")
        testKit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        val get = GET(Key("foo"))

        val futureSetResult = redis ? set
        val futureGetResult = redis ? get

        assertResult(set -> RSimpleString.OK) {
          await(futureSetResult)
        }
        assertResult(get -> RBulkString("bar")) {
          await(futureGetResult)
        }

        testKit.watch(redis)
        redis ! SHUTDOWN()
        testKit.expectTerminated(redis)
      }
    }
  }

  it should "send setup commands on connection" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val testKit = new TestKit(system)

        val setupCommands = List(
          SET(Key("A"), ByteString("ABC")),
          SET(Key("X"), ByteString("XYZ")))
        val redis = ProxyingParent(RedisCommandReplyActor.props(address, setupCommands, Some("ready")), testKit.testActor, "redis-actor")
        testKit.expectMsg("ready")

        val getA = GET(Key("A"))
        val a = redis ? getA

        val getX = GET(Key("X"))
        val x = redis ? getX

        assertResult(getA -> RBulkString("ABC")) {
          await(a)
        }
        assertResult(getX -> RBulkString("XYZ")) {
          await(x)
        }

        testKit.watch(redis)
        redis ! SHUTDOWN()
        testKit.expectTerminated(redis)
      }
    }
  }

}
