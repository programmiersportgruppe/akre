package org.programmiersportgruppe.redis

import akka.util.ByteString


class PushParserReplyReconstructor extends ReplyReconstructor {
  var parser: Parser[RValue] = RValueParser

  override def process(data: ByteString)(handleReply: RValue => _) = {
    parser.parse(data) match {
      case continuation: Parser[RValue] =>
        parser = continuation
      case CompleteParse(value, remainder) =>
        parser = RValueParser
        handleReply(value)
        process(remainder)(handleReply)
    }
  }
}
