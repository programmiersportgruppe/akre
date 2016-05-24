package org.programmiersportgruppe.redis.client

import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.{ GET, SET }
import org.programmiersportgruppe.redis.test.{ ActorSystemAcceptanceTest, ProxyingParent }

class RedisCommandReplyActorTest extends ActorSystemAcceptanceTest {

  behavior of "a connection to the Redis server"

  it should "persist keys to the database" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val testKit = new TestKit(system)

        val redis = ProxyingParent(RedisCommandReplyActor.props(address, messageToParentOnConnected = Some("ready")), testKit.testActor, "redisCommandReplyActor")
        testKit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        assertResult(set -> RSimpleString.OK) {
          await(redis ? set)
        }

        val get = GET(Key("foo"))
        assertResult(get -> RBulkString("bar")) {
          await(redis ? get)
        }
      }
    }
  }

  it should "handle receiving multiple commands at once" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val testKit = new TestKit(system)

        val ref = ProxyingParent(RedisCommandReplyActor.props(address, messageToParentOnConnected = Some("ready")), testKit.testActor, "redisCommandReplyActor")
        testKit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        val get = GET(Key("foo"))

        val futureSetResult = ref ? set
        val futureGetResult = ref ? get

        assertResult(set -> RSimpleString.OK) {
          await(futureSetResult)
        }
        assertResult(get -> RBulkString("bar")) {
          await(futureGetResult)
        }
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
        val ref = ProxyingParent(RedisCommandReplyActor.props(address, setupCommands, Some("ready")), testKit.testActor, "redisCommandReplyActor")
        testKit.expectMsg("ready")

        val getA = GET(Key("A"))
        val a = ref ? getA

        val getX = GET(Key("X"))
        val x = ref ? getX

        assertResult(getA -> RBulkString("ABC")) {
          await(a)
        }
        assertResult(getX -> RBulkString("XYZ")) {
          await(x)
        }
      }
    }
  }

}
