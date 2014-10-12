package org.programmiersportgruppe.redis.protocol

import akka.util.{ByteString, ByteStringBuilder}

import org.programmiersportgruppe.redis._


object RValueSerializer {
  val newLine = Array[Byte]('\r', '\n')
  val nullBulkStringSerialization = Array[Byte]('$', '-', '1') ++ newLine

  def serialize(value: RValue): ByteString = {
    val builder = new ByteStringBuilder
    serializeTo(builder)(value)
    builder.result()
  }

  def serializeTo(builder: ByteStringBuilder)(value: RValue): ByteStringBuilder = {
    value match {
      case s: RSimpleString => builder.putByte('+').append(s.asByteString).putBytes(newLine)
      case e: RError => builder.putByte('-').append(e.asByteString).putBytes(newLine)
      case i: RInteger => builder.putByte(':').append(i.asByteString).putBytes(newLine)
      case RBulkString(Some(data)) => builder.putByte('$').append(ByteString(data.length.toString)).putBytes(newLine).append(data).putBytes(newLine)
      case RBulkString(None) => builder.putBytes(nullBulkStringSerialization)
      case RArray(items) => items.foldLeft(builder.putByte('*').append(ByteString(items.length.toString)).putBytes(newLine))((b, i) => serializeTo(b)(i))
    }
  }
}
