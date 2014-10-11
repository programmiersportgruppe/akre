package org.programmiersportgruppe.redis.protocol

import scala.language.implicitConversions
import scala.util.parsing.combinator.Parsers

import akka.util.ByteString

import org.programmiersportgruppe.redis._


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
  //        Success(ByteString(data), nextIn)
    val (bytes, remainder) = in.asInstanceOf[ByteStringReader].extract(n)
    bytes match {
      case Some(data) => Success(data, remainder)
      case None => Failure(UnifiedProtocolParsers.EndOfInputFailureString, remainder)
    }
  }

  implicit def stringLiteral(literal: String): Parser[String] = accept(literal.getBytes("UTF-8").toList) ^^^ literal

  def newline: Parser[ByteString] = "\r\n" ^^^ ByteString.empty
  def newlineTerminatedByteString: Parser[ByteString] = newline | (elem("any byte", _ => true) ~! newlineTerminatedByteString ^^ { case ~(c, cs) => c +: cs })
  def newlineTerminatedString: Parser[String] = newlineTerminatedByteString ^^ (_.utf8String)

  def terminatedInteger: Parser[Long] = newlineTerminatedString ^^ (_.toLong)


  def reply: Parser[RValue] = statusReply | errorReply | integerReply | bulkReply | multiBulkReply

  def statusReply: Parser[RSimpleString] = "+" ~> commit(newlineTerminatedString) ^^ RSimpleString.apply
  def errorReply: Parser[RError] = "-" ~> commit(newlineTerminatedString) ^^ RError.apply
  def integerReply: Parser[RInteger] = ":" ~> commit(terminatedInteger) ^^ RInteger.apply
  def bulkReply: Parser[RBulkString] = "$" ~> commit(terminatedInteger >> {
    case -1 => success(None)
    case size => bytes(size.toInt) <~ newline ^^ Some.apply
  }) ^^ RBulkString.apply
  def multiBulkReply: Parser[RArray] = ("*" ~> commit(terminatedInteger)).flatMap(n => repN(n.toInt, reply)) ^^ RArray
}

object UnifiedProtocolParsers {
  val EndOfInputFailureString = "end of input"
}
