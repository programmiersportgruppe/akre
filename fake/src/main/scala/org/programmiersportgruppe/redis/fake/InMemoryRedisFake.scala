package org.programmiersportgruppe.redis.fake

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands._


class InMemoryRedisFake {

  var value: ByteString = null

  def execute(command: RecognisedCommand): RValue = command match {
    case GET(key) => RBulkString(value)
    case SET(_, value, _, _) =>
      this.value = value
      RSimpleString.OK
    case c => RError("I have no clue what this command is! " + c)
  }

}
