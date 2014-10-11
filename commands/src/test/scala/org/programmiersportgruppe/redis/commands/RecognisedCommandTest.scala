package org.programmiersportgruppe.redis.commands

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.test.Test


class RecognisedCommandTest extends Test {

  behavior of classOf[RecognisedCommand].getName

  it should "have a short string representation" in {
    assertResult("RecognisedCommand: SET <bytes=3> <bytes=5> EX 7") {
      SET(Key("abc"), ByteString("yays!"), Some(ExpiresInSeconds(7))).toString
    }
  }

}
