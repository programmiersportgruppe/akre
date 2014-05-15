package org.programmiersportgruppe.redis

import akka.util.ByteString
import org.scalatest.FunSuite

class CommandTest extends FunSuite {

  test("should have short string representation") {
    assertResult("SET <bytes=3> <bytes=5> EX 7") {
      SET(Command.Key("abc"), ByteString("yays!"), Some(ExpiresInSeconds(7))).toString
    }
  }

}
