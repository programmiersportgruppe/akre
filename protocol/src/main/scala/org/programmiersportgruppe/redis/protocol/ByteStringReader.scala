package org.programmiersportgruppe.redis.protocol

import scala.util.parsing.input.{OffsetPosition, Reader}

import akka.util.ByteString


//case class ByteStringOffsetPosition(byteString: ByteString, offset: Int) extends Position{
//  def line: Int = ???
//
//  def column: Int = ???
//
//  protected def lineContents: String = ???
//}

case class ByteStringReader(byteString: ByteString, override val offset: Int) extends Reader[Byte] {
  assert(offset <= byteString.length, s"Offset ($offset) must be less than or equal to string length (${byteString.length})")

  override def atEnd = offset == byteString.length
  override def first = byteString(offset)
  override def pos = new OffsetPosition(byteString.utf8String, offset) // TODO: unhack
//  override def pos = ByteStringOffsetPosition(byteString, offset)
  override def rest = new ByteStringReader(byteString, offset + 1)

  def extract(count: Int): (Option[ByteString], ByteStringReader) = {
    val newOffset = offset + count
    if (newOffset < byteString.length)
      Some(byteString.slice(offset, newOffset)) -> new ByteStringReader(byteString, newOffset)
    else
      None -> this
  }

  def asByteString: ByteString = byteString.drop(offset)
}
