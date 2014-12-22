package org.programmiersportgruppe.redis

import org.programmiersportgruppe.redis.test.Test
import org.programmiersportgruppe.redis.CommandArgument.ImplicitConversions._


class UntypedCommandTest extends Test {

  behavior of classOf[UntypedCommand].getName

  it should "have a short string representation" in {
    assertResult("""UntypedCommand: SET "zuh?" "yays!" "EX" "7"""") {
      UntypedCommand("SET", Key("zuh?"), "yays!", Constant("EX"), 7).toString
    }
  }

}
