package org.programmiersportgruppe.redis

import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit}
import akka.util.ByteString

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest


class RedisConnectionActorTest extends ActorSystemAcceptanceTest {

  behavior of "a connection to the Redis server"

  it should "persist keys to the database" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val kit = new TestKit(system)
        val ref = TestActorRef(RedisConnectionActor.props(address, Some("ready")), kit.testActor, "SOT")
        kit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        assertResult(set -> StatusReply("OK")) {
          await(ref ? set)
        }

        val get = GET(Key("foo"))
        assertResult(get -> BulkReply(Some(ByteString("bar")))) {
          await(ref ? get)
        }
      }
    }
  }

  it should "handle receiving multiple commands at once" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val kit = new TestKit(system)
        val ref = TestActorRef(RedisConnectionActor.props(address, Some("ready")), kit.testActor, "SOT")
        kit.expectMsg("ready")

        val set = SET(Key("foo"), ByteString("bar"))
        val get = GET(Key("foo"))

        val futureSetResult = ref ? set
        val futureGetResult = ref ? get

        assertResult(set -> StatusReply("OK")) {
          await(futureSetResult)
        }
        assertResult(get -> BulkReply(Some(ByteString("bar")))) {
          await(futureGetResult)
        }
      }
    }
  }

}
