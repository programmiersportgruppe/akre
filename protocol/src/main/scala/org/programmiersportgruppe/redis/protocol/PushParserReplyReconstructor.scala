package org.programmiersportgruppe.redis.protocol

import akka.util.ByteString

import org.programmiersportgruppe.redis._


class PushParserReplyReconstructor extends ReplyReconstructor {

  private[this] var parser: Parser[RValue] = RValueParser

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
