package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed trait RValue


sealed trait RScalar extends RValue

sealed trait RSimpleScalar extends RScalar {
  def asByteString: ByteString
}


case class RError(value: String) extends RSimpleScalar {
  override def asByteString = ByteString(value)

  def prefix: String = value.indexOf(' ') match {
    case -1 => value
    case i  => value.substring(0, i)
  }
}


sealed trait RSuccessValue extends RValue


case class RSimpleString(value: String) extends RSuccessValue with RSimpleScalar {
  override def asByteString = ByteString(value)
}

object RSimpleString {
  val OK = RSimpleString("OK")
}


case class RInteger(value: Long) extends RSuccessValue with RSimpleScalar {
  override def asByteString = ByteString(value.toString)
}

object RInteger {
  def apply(value: String): RInteger = new RInteger(value.toLong)
}


case class RBulkString(data: Option[ByteString]) extends RSuccessValue with RScalar {
  override def toString = data.fold("<null>")(d => s"<bytes=${d.size}>")
}

object RBulkString {
  def apply(bulkString: ByteString): RBulkString = new RBulkString(Some(bulkString))
  def apply(s: String): RBulkString = RBulkString(ByteString(s))

  val Null = RBulkString(None)
}


case class RArray(items: Seq[RValue]) extends RSuccessValue
