package org.programmiersportgruppe.redis.protocol

import akka.util.ByteString

import org.programmiersportgruppe.redis.{Command, RArray, RBulkString}


object CommandSerializer {

  def asRArray(command: Command): RArray =
    RArray(command.nameAndArguments.map(arg => RBulkString(arg)))

  def serialize(command: Command): ByteString =
    RValueSerializer.serialize(asRArray(command))

}
