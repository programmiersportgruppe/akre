package org.programmiersportgruppe.redis

import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit}
import akka.util.ByteString

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import Command.Key


class RedisConnectionActorTest extends ActorSystemAcceptanceTest {

  behavior of "a connection to the Redis server"

  it should "persist keys to the database" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val kit = new TestKit(system)
        val ref = TestActorRef(RedisConnectionActor.props(address, messageToParentOnConnected = Some("ready")), kit.testActor, "SOT")
        kit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        assertResult(set -> RSimpleString.OK) {
          await(ref ? set)
        }

        val get = GET(Key("foo"))
        assertResult(get -> RBulkString("bar")) {
          await(ref ? get)
        }
      }
    }
  }

  it should "handle receiving multiple commands at once" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val kit = new TestKit(system)
        val ref = TestActorRef(RedisConnectionActor.props(address, messageToParentOnConnected = Some("ready")), kit.testActor, "SOT")
        kit.expectMsg("ready")

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
        val kit = new TestKit(system)
        val setupCommands = List(
          SET(Key("A"), ByteString("ABC")),
          SET(Key("X"), ByteString("XYZ")))
        val ref = TestActorRef(RedisConnectionActor.props(address, setupCommands, Some("ready")), kit.testActor, "SOT")
        kit.expectMsg("ready")

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
