package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed trait ParseResult[+T] {
  def map[S](f: T => S): ParseResult[S]
}

case class CompleteParse[+T](value: T, remainder: ByteString) extends ParseResult[T] {
  override def map[S](f: T => S): CompleteParse[S] = new CompleteParse[S](f(value), remainder)
}

trait Parser[+T] extends ParseResult[T] { top =>
  final def parse(bytes: ByteString): ParseResult[T] =
    if (bytes.isEmpty) this
    else parseNonEmpty(bytes)

  def parseNonEmpty(bytes: ByteString): ParseResult[T]

  override def map[S](f: (T) => S): Parser[S] = new Parser[S] {
    override def parseNonEmpty(bytes: ByteString) = top.parseNonEmpty(bytes).map(f)
  }

  def flatMap[S](f: T => ParseResult[S]): Parser[S] = new Parser[S] {
    override def parseNonEmpty(bytes: ByteString) = top.parseNonEmpty(bytes) match {
      case CompleteParse(value, remainder) => f(value) match {
        case CompleteParse(mappedValue, mappedRemainder) => CompleteParse(mappedValue, remainder.ensuring(mappedRemainder.isEmpty))
        case parser: Parser[S] => parser.parse(remainder)
      }
      case continuation: Parser[T] => continuation.flatMap(f)
    }
  }
}

object LineParser extends Parser[String] { top =>
  def parseNonEmpty(bytes: ByteString) = parseNonEmpty(bytes, 0)

  def parseNonEmpty(bytes: ByteString, start: Int): ParseResult[String] =
    bytes.indexOf('\r', start) match {
      case -1 => new Continuation(bytes)
      case i if i == bytes.length -1 => new Continuation(bytes)
      case i if bytes(i + 1) == '\n' => CompleteParse(bytes.take(i).utf8String, bytes.drop(i + 2))
      case i => parseNonEmpty(bytes, i + 1)
    }

  class Continuation(partial: ByteString) extends Parser[String] {
    override def parseNonEmpty(bytes: ByteString) = top.parseNonEmpty(partial ++ bytes, partial.length - 1)
  }
}


object RValueParser extends Parser[RValue] {
  val IntegerParser: Parser[Long] = LineParser.map(java.lang.Long.parseLong)

  val RSimpleStringParser: Parser[RSimpleString] = LineParser.map(RSimpleString.apply)
  val RErrorParser: Parser[RError] = LineParser.map(RError.apply)
  val RIntegerParser: Parser[RInteger] = IntegerParser.map(RInteger.apply)
  val RBulkStringParser: Parser[RBulkString] = IntegerParser.flatMap {
    case -1 => CompleteParse(RBulkString(None), ByteString.empty)
    case n => new NonNullRBulkStringParser(n.toInt)
  }
  val RArrayParser: Parser[RArray] = IntegerParser.flatMap {
    case 0 => CompleteParse(RArray(Nil), ByteString.empty)
    case n => new RArrayItemsParser(n.toInt)
  }

  override def parseNonEmpty(bytes: ByteString) =
    (bytes.head match {
      case '+' => RSimpleStringParser  : Parser[RValue]
      case '-' => RErrorParser : Parser[RValue]
      case ':' => RIntegerParser : Parser[RValue]
      case '$' => RBulkStringParser : Parser[RValue]
      case '*' => RArrayParser : Parser[RValue]
    }).parse(bytes.tail)
}

class NonNullRBulkStringParser(byteStringLength: Int) extends Parser[RBulkString] { top =>
  val lengthWithLineTerminator = byteStringLength + 2
  override def parseNonEmpty(bytes: ByteString) =
    if (bytes.length >= lengthWithLineTerminator) new CompleteParse(RBulkString(Some(bytes.take(byteStringLength))), bytes.drop(lengthWithLineTerminator))
    else new Parser[RBulkString] {
      override def parseNonEmpty(additional: ByteString) = top.parseNonEmpty(bytes ++ additional)
    }
}

class RArrayItemsParser(length: Int) extends Parser[RArray] { top =>
  override def parseNonEmpty(bytes: ByteString) = parseNonEmpty(bytes, Nil, RValueParser)

  def parseNonEmpty(bytes: ByteString, values: Seq[RValue], nextParser: Parser[RValue]): ParseResult[RArray] = {
    nextParser.parseNonEmpty(bytes) match {
      case CompleteParse(value, remainder) =>
        val newValues = values :+ value
        if (newValues.length < length) parseNonEmpty(remainder, newValues, RValueParser)
        else CompleteParse(RArray(newValues), remainder)
      case continuation: Parser[RValue] => new Parser[RArray] {
        override def parseNonEmpty(additional: ByteString) = top.parseNonEmpty(bytes ++ additional, values, continuation)
      }
    }
  }
}
