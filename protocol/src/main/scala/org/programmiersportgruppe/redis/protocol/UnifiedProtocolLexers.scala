package org.programmiersportgruppe.redis.protocol

import scala.util.parsing.combinator.Parsers

import akka.util.ByteString


sealed abstract class Token
sealed abstract class Sigil(value: Byte) extends Token
case object StatusSigil extends Sigil('+')
case object ErrorSigil extends Sigil('-')
case object IntegerSigil extends Sigil(':')
case object BulkSigil extends Sigil('$')
case object MultiBulkSigil extends Sigil('*')
case class IntegerNewlineToken(value: Long) extends Token
case class ByteStringToken(value: ByteString) extends Token
case object NewlineToken extends Token

class UnifiedProtocolLexers extends Parsers {
  override type Elem = Byte


//  def token: Parser[Token] =
}
