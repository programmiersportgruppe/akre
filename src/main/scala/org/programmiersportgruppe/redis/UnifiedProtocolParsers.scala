package org.programmiersportgruppe.redis

import scala.util.parsing.combinator.Parsers
import akka.util.ByteString


trait UnifiedProtocolParsers extends Parsers {
  override type Elem = Byte

  def byte: Parser[Byte] = Parser(in => Success(in.first, in.rest))
  def bytes(n: Int): Parser[ByteString] = Parser { in =>
  //        val data = new Array[Byte](n)
  //        var nextIn = in
  //        for (i <- 0 to n) {
  //            data(i) = nextIn.first
  //            nextIn = nextIn.rest
  //        }
  //        Success(data, nextIn)
    val (bytes, remainder) = in.asInstanceOf[ByteStringReader].extract(n)
    Success(bytes, remainder)
  }

  implicit def stringLiteral(literal: String): Parser[String] = accept(literal.getBytes("UTF-8").toList) ^^^ literal

  def newline: Parser[ByteString] = "\r\n" ^^^ ByteString.empty
  def newlineTerminatedByteString: Parser[ByteString] = newline | (elem("any byte", _ => true) ~! newlineTerminatedByteString ^^ { case ~(c, cs) => c +: cs })
  def newlineTerminatedString: Parser[String] = newlineTerminatedByteString ^^ (_.utf8String)

  def terminatedInteger: Parser[Long] = newlineTerminatedString ^^ (_.toLong)


  def reply: Parser[Reply] = statusReply | errorReply | integerReply | bulkReply | multiBulkReply

  def statusReply: Parser[StatusReply] = "+" ~> commit(newlineTerminatedString) ^^ StatusReply
  def errorReply: Parser[ErrorReply] = "-" ~> commit(newlineTerminatedString) ^^ ErrorReply
  def integerReply: Parser[IntegerReply] = ":" ~> commit(terminatedInteger) ^^ IntegerReply
  def bulkReply: Parser[BulkReply] = "$" ~> commit(terminatedInteger >> {
    case -1 => success(None)
    case size => bytes(size.toInt) <~ newline ^^ Some.apply
  }) ^^ BulkReply
  def multiBulkReply: Parser[MultiBulkReply] = ("*" ~> commit(terminatedInteger)).flatMap(n => repN(n.toInt, reply)) ^^ MultiBulkReply
}
