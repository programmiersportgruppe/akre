package org.programmiersportgruppe.redis

import akka.util.ByteString


class ParserCombinatorReplyReconstructor extends ReplyReconstructor with UnifiedProtocolParsers {

  private var buffer: ByteString = ByteString.empty

  def extractNext: Option[Reply] = {
    reply(new ByteStringReader(buffer, 0)) match {
      case Success(reply, remainder) =>
        buffer = remainder.asInstanceOf[ByteStringReader].asByteString
        Some(reply)
      case Failure("end of input", _) => None
      case f: Failure => throw new RuntimeException("Unexpected parse failure: " + f + "\nUnconsumed input: " + f.next.asInstanceOf[ByteStringReader].asByteString)
      case e: Error => throw new RuntimeException("Parse error: " + e)
    }
  }

  def extractStream: Stream[Reply] = extractNext match {
    case Some(reply) => reply #:: extractStream
    case None => Stream.empty
  }

  def process(data: ByteString)(handleReply: (Reply) => _) {
    buffer ++= data
    extractStream.foreach(handleReply)
  }

}
