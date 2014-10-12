package org.programmiersportgruppe.redis.fake

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.{GET, SET}
import org.programmiersportgruppe.redis.test.Test


class InMemoryRedisFakeTest extends Test {

    behavior of "an in-memory Redis fake"

    it should "return persisted values" in {
        val redis = new InMemoryRedisFake

        assertResult(RSimpleString.OK) {
            redis.execute(SET(Key("foo"), ByteString("bar")))
        }

        assertResult(RBulkString("bar")) {
            redis.execute(GET(Key("foo")))
        }
    }

}
