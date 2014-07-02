package org.programmiersportgruppe.redis

import akka.util.ByteString


class Command(val name: String, args: Seq[Command.Argument]) {
  def argsWithCommand = name.split(" ").map(RSimpleString(_)).toSeq ++ args
  def asRArray = RArray(argsWithCommand.map(_.asRBulkString))

  def execute(implicit client: RedisClient) = client.execute(this)

  /**
   * A short rendering of the command,
   * appropriate for use in exception messages and when debugging
   * without much risk of being ridiculously long.
   */
  override def toString = argsWithCommand.map {
    case RSimpleString(string) => string
    case RInteger(value) => value.toString
    case RBulkString.Null => "<null>"
    case RBulkString(Some(bytes)) => s"<bytes=${bytes.length}>"
  }.mkString(" ")

  lazy val serialised: ByteString = RValueSerializer.serialize(asRArray)
}

object Command {
  type Argument = RScalar with RSuccessValue
  type Key = RBulkString
  val Key = RBulkString
}
