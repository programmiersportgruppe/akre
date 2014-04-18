package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed trait RValue {
  def sigil: Byte
}

sealed trait RScalar extends RValue {
  def asRBulkString: RBulkString
}

sealed trait RSimpleScalar extends RScalar {
  def asByteString: ByteString
  lazy val asString: String = asByteString.utf8String
  override def asRBulkString: RBulkString = RBulkString(asByteString)
}


case class RError(value: String) extends RSimpleScalar {
  override lazy val asByteString: ByteString = ByteString(asString)
  override lazy val asString: String = value

  override def sigil = '-'

  def prefix: String = asString.indexOf(' ') match {
    case -1 => asString
    case i  => asString.substring(0, i)
  }
}
object RError {
  def apply(error: ByteString) = new RError(error.utf8String)
}

sealed trait RSuccessValue extends RValue

case class RSimpleString(value: String) extends RSuccessValue with RSimpleScalar {
  override lazy val asByteString: ByteString = ByteString(asString)
  override lazy val asString: String = value

  override def sigil = '+'
}
object RSimpleString {
//  def apply(simpleString: ByteString) = new RSimpleString(simpleString.utf8String)

  val OK = RSimpleString("OK")
}

case class RInteger(value: Long) extends RSuccessValue with RSimpleScalar {
  override val asByteString = ByteString(value.toString)

  override def sigil = ':'
}
object RInteger {
  def apply(value: String) = new RInteger(value.toLong)
  def apply(value: ByteString): RInteger = RInteger(value.utf8String)
}

case class RBulkString(data: Option[ByteString]) extends RSuccessValue with RScalar {
  override def sigil = '$'

  override def asRBulkString = this
}
object RBulkString {
  def apply(bulkString: ByteString) = new RBulkString(Some(bulkString))
  def apply(s: String): RBulkString = RBulkString(ByteString(s))

  val Null = RBulkString(None)
}

case class RArray(items: Seq[RValue]) extends RSuccessValue {
  override def sigil = '*'
}
