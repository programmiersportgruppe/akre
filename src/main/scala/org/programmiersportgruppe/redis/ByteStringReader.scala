package org.programmiersportgruppe.redis

import scala.util.parsing.input.{OffsetPosition, Position, NoPosition, Reader}
import akka.util.ByteString

//case class ByteStringOffsetPosition(byteString: ByteString, offset: Int) extends Position{
//  def line: Int = ???
//
//  def column: Int = ???
//
//  protected def lineContents: String = ???
//}

class ByteStringReader(byteString: ByteString, offset: Int) extends Reader[Byte] {
  assert(offset <= byteString.length)

  override def atEnd = offset == byteString.length
  override def first = byteString(offset)
  override def pos = new OffsetPosition(byteString.utf8String, offset) // TODO: unhack
//  override def pos = ByteStringOffsetPosition(byteString, offset)
  override def rest = new ByteStringReader(byteString, offset + 1)

  def extract(count: Int): (ByteString, ByteStringReader) = {
    val newOffset = offset + count
    byteString.slice(offset, newOffset) -> new ByteStringReader(byteString, newOffset)
  }

  def asByteString = byteString.drop(offset)
}
