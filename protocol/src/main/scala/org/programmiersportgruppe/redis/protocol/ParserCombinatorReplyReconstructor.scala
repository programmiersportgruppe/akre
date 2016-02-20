package org.programmiersportgruppe.redis.protocol

import akka.util.ByteString

import org.programmiersportgruppe.redis._


class ParserCombinatorReplyReconstructor extends ReplyReconstructor with UnifiedProtocolParsers {

  private[this] var buffer: ByteString = ByteString.empty

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

  def process(data: ByteString)(handleReply: (RValue) => _): Unit = {
    buffer ++= data
    extractStream.foreach(handleReply)
  }

}
