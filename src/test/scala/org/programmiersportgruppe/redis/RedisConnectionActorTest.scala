package org.programmiersportgruppe.redis

import akka.pattern.ask
import akka.testkit.TestActorRef

import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest
import org.programmiersportgruppe.redis.RedisConnectionActor.{Connected, WaitForConnection}
import akka.util.ByteString


class RedisConnectionActorTest extends ActorSystemAcceptanceTest {

  behavior of "a connection to the Redis server"

  it should "connect to the Redis server" in {
    withRedisServer { serverAddress =>
      withActorSystem { implicit actorSystem =>
        val ref = TestActorRef(RedisConnectionActor.props(serverAddress))

        assertResult(Connected) {
          await(ref ? WaitForConnection)
        }
      }
    }
  }

  it should "persist keys to the database" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val ref = TestActorRef(RedisConnectionActor.props(address))

        await(ref ? WaitForConnection)

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
        val ref = TestActorRef(RedisConnectionActor.props(address))

        await(ref ? WaitForConnection)

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

  it should "throw when receiving commands before the connection is established" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val ref = TestActorRef(RedisConnectionActor.props(address))

        val futureConnected = ref ? WaitForConnection

        val set = SET(Key("foo"), ByteString("bar"))
        intercept[RuntimeException] {
          try {
            await(ref ? set)
          } finally {
            assertResult(false, "For the test to be valid, we can't have been notified of a successful connection yet") {
              futureConnected.isCompleted
            }
          }
        }.getMessage should startWith ("Received command before connected: SET")
      }
    }
  }

}
