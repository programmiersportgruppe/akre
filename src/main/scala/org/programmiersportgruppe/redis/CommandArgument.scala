package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed trait CommandArgument { val toByteString: ByteString }

case class Key(key: ByteString) extends CommandArgument {
  override lazy val toByteString = key
  override def toString = s"Key(${key.utf8String})"
}
object Key { def apply(key: String) = new Key(ByteString(key)) }

case class RByteString(value: ByteString) extends CommandArgument { override lazy val toByteString = value }

case class RString(value: String) extends CommandArgument { override lazy val toByteString = ByteString(value) }

case class RInteger(value: Long) extends CommandArgument { override lazy val toByteString = ByteString(value.toString) }
