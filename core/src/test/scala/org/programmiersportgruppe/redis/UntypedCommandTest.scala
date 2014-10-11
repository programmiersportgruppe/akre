package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.Test


class UntypedCommandTest extends Test {

  behavior of classOf[UntypedCommand].getName

  it should "have a short string representation" in {
    assertResult("UntypedCommand: SET <bytes=5> EX 7") {
      UntypedCommand("SET", RBulkString("yays!"), RSimpleString("EX"), RInteger(7)).toString
    }
  }

}
