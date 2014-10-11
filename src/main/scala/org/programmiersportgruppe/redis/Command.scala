package org.programmiersportgruppe.redis

import scala.concurrent.Future

import akka.util.ByteString

import org.programmiersportgruppe.redis.Command.Argument


class Command(val name: String, args: Seq[Argument]) {
  def argsWithCommand: Seq[Argument] = name.split(" ").toSeq.map(RSimpleString(_)) ++ args
  def asRArray: RArray = RArray(argsWithCommand.map(_.asRBulkString))

  lazy val serialised: ByteString = RValueSerializer.serialize(asRArray)

  def execute(implicit client: RedisClient): Future[RSuccessValue] = client.execute(this)

  /**
   * A short rendering of the command,
   * appropriate for use in exception messages and when debugging
   * without much risk of being ridiculously long.
   */
  override def toString = argsWithCommand.map {
    case RSimpleString(string) => string
    case RInteger(value) => value.toString
    case RBulkString(None) => "<null>"
    case RBulkString(Some(bytes)) => s"<bytes=${bytes.length}>"
  }.mkString(" ")
}

object Command {
  type Argument = RScalar with RSuccessValue
  type Key = RBulkString
  val Key = RBulkString
}
