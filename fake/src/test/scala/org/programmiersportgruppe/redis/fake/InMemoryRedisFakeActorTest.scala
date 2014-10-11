package org.programmiersportgruppe.redis.fake

import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit}
import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.{GET, SET}
import org.programmiersportgruppe.redis.test.ActorSystemAcceptanceTest


class InMemoryRedisFakeActorTest extends ActorSystemAcceptanceTest {

    behavior of "an in-memory Redis fake"

    it should "Persist and return values" in {
        withActorSystem { implicit system =>
            val kit = new TestKit(system)
            val ref = TestActorRef(InMemoryRedisFakeActor.props(), kit.testActor, "SOT")

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
