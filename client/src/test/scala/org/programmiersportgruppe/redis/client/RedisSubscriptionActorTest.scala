package org.programmiersportgruppe.redis.client

import scala.collection.mutable

import akka.testkit.{TestActorRef, TestKit}

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.client.RedisSubscriptionActor.PubSubMessage
import org.programmiersportgruppe.redis.commands.PUBLISH
import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest


class RedisSubscriptionActorTest extends ActorSystemAcceptanceTest {

  behavior of "a pubsub subscription connection to the Redis server"

  it should "send the onConnected message" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val kit = new TestKit(system)
        TestActorRef(RedisSubscriptionActor.props(address, messageToParentOnSubscribed = Some("ready"), Seq("chan A"), _ => ()), kit.testActor, "SOT")
        kit.expectMsg("ready")
      }
    }
  }

  it should "receive own messages for subscribed channels" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val messages = mutable.Queue[PubSubMessage]()
        val onMessage = (m: PubSubMessage) => messages.enqueue(m)

        val subscriptionKit = new TestKit(system)
        TestActorRef(RedisSubscriptionActor.props(address, messageToParentOnSubscribed = Some("sub ready"), Seq("chan A", "chan C"), onMessage), subscriptionKit.testActor, "SOT")

        val normalKit = new TestKit(system)
        val normal = TestActorRef(RedisCommandReplyActor.props(address, messageToParentOnConnected = Some("pub ready")), normalKit.testActor, "SOT")

        normalKit.expectMsg("pub ready")
        subscriptionKit.expectMsg("sub ready")

        normal ! PUBLISH("chan A", "a message")
        normal ! PUBLISH("chan B", "b message")
        normal ! PUBLISH("chan C", "c message")

        eventually {
          assert(messages == Seq(PubSubMessage("chan A", "a message"), PubSubMessage("chan C", "c message")))
        }
      }
    }
  }

}
