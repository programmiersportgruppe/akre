package org.programmiersportgruppe.redis

import akka.util.ByteString


class ParserCombinatorReplyReconstructor extends ReplyReconstructor with UnifiedProtocolParsers {

  private var buffer: ByteString = ByteString.empty

  def extractNext: Option[RValue] = {
    reply(new ByteStringReader(buffer, 0)) match {
      case Success(reply, remainder) =>
        buffer = remainder.asInstanceOf[ByteStringReader].asByteString
        Some(reply)
      case NoSuccess(UnifiedProtocolParsers.EndOfInputFailureString, _) => None
      case f @ NoSuccess(_, _) => throw new RuntimeException("Unexpected parse failure: " + f + "\nUnconsumed input: " + f.next.asInstanceOf[ByteStringReader].asByteString)
    }
  }

  def extractStream: Stream[RValue] = extractNext match {
    case Some(reply) => reply #:: extractStream
    case None => Stream.empty
  }

  def process(data: ByteString)(handleReply: (RValue) => _) {
    buffer ++= data
    extractStream.foreach(handleReply)
  }

}
