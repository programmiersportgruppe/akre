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
  override def asRBulkString: RBulkString = RBulkString(asByteString)
}

case class RError(value: String) extends RSimpleScalar {
  override def asByteString = ByteString(value)

  override def sigil = '-'

  def prefix: String = value.indexOf(' ') match {
    case -1 => value
    case i  => value.substring(0, i)
  }
}
object RError {
//  def apply(error: ByteString): RError = new RError(error.utf8String)
}

sealed trait RSuccessValue extends RValue

case class RSimpleString(value: String) extends RSuccessValue with RSimpleScalar {
  override def asByteString = ByteString(value)

  override def sigil = '+'

  override def toString = value
}
object RSimpleString {
//  def apply(simpleString: ByteString) = new RSimpleString(simpleString.utf8String)

  val OK = RSimpleString("OK")
}

case class RInteger(value: Long) extends RSuccessValue with RSimpleScalar {
  override def asByteString = ByteString(value.toString)

  override def sigil = ':'

  override def toString = value.toString
}
object RInteger {
  def apply(value: String): RInteger = new RInteger(value.toLong)
//  def apply(value: ByteString): RInteger = RInteger(value.utf8String)
}

case class RBulkString(data: Option[ByteString]) extends RSuccessValue with RScalar {
  override def sigil = '$'

  override def asRBulkString = this

  override def toString = data.fold("<null>")(d => s"<bytes=${d.size}>")
}
object RBulkString {
  def apply(bulkString: ByteString): RBulkString = new RBulkString(Some(bulkString))
  def apply(s: String): RBulkString = RBulkString(ByteString(s))

  val Null = RBulkString(None)
}

case class RArray(items: Seq[RValue]) extends RSuccessValue {
  override def sigil = '*'
}
