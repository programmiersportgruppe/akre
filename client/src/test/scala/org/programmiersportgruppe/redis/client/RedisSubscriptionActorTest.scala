package org.programmiersportgruppe.redis.client

import scala.collection.mutable

import akka.testkit.TestKit
import org.scalatest.time.{ Millis, Span }

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.client.RedisSubscriptionActor.PubSubMessage
import org.programmiersportgruppe.redis.commands.PUBLISH
import org.programmiersportgruppe.redis.test.{ ActorSystemAcceptanceTest, ProxyingParent }


class RedisSubscriptionActorTest extends ActorSystemAcceptanceTest {

  behavior of "a pubsub subscription connection to the Redis server"

  it should "send the onConnected message" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val testKit = new TestKit(system)
        ProxyingParent(RedisSubscriptionActor.props(address, messageToParentOnSubscribed = Some("ready"), Seq("chan A"), _ => ()), testKit.testActor, "redisSubscription")
        testKit.expectMsg("ready")
      }
    }
  }

  it should "receive own messages for subscribed channels" in {
    withRedisServer { address =>
      withActorSystem { implicit system =>
        val messages = mutable.Queue[PubSubMessage]()
        val onMessage = (m: PubSubMessage) => messages.enqueue(m)

        val testKit = new TestKit(system)

        val _         = ProxyingParent(RedisSubscriptionActor.props(address, messageToParentOnSubscribed = Some("sub ready"), Seq("chan A", "chan C"), onMessage), testKit.testActor, "subscriber")
        val publisher = ProxyingParent(RedisCommandReplyActor.props(address, messageToParentOnConnected = Some("pub ready")), testKit.testActor, "publisher")

        testKit.expectMsgAllOf("pub ready", "sub ready")

        publisher ! PUBLISH("chan A", "a message")
        publisher ! PUBLISH("chan B", "b message")
        publisher ! PUBLISH("chan C", "c message")

        implicit def patienceConfig = super.patienceConfig.copy(Span(300, Millis))
        eventually {
          val formattedMessages =
            messages
              .map { case PubSubMessage(channel, message) => s"[${channel.utf8String}]: ${message.utf8String}" }
              .mkString("; ")

          assert(formattedMessages == "[chan A]: a message; [chan C]: c message")
        }
      }
    }
  }

}
