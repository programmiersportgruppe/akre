package org.programmiersportgruppe.redis

import akka.util.{ByteString, ByteStringBuilder}


abstract class Command(commandName: String, args: Seq[CommandArgument]) {

  val name = commandName
  lazy val argsWithCommand = RString(name) +: args

  def execute(implicit client: RedisClient) = client.execute(this)

  /**
   * A short rendering of the command,
   * appropriate for use in exception messages and when debugging
   * without much risk of being ridiculously long.
   */
  override def toString = argsWithCommand.map {
    case RString(value) => value
    case Key(key) => "<key>"
    case RByteString(byteString) => "<string>"
    case RInteger(value) => value.toString
  }.mkString(" ")

  lazy val serialised: ByteString = {
    val builder = new ByteStringBuilder
    builder.putByte('*').append(ByteString(argsWithCommand.length.toString)).putByte('\r').putByte('\n')
    argsWithCommand.map(_.toByteString).foreach(bytes =>
      builder
        .putByte('$').append(ByteString(bytes.length.toString)).putByte('\r').putByte('\n')
        .append(bytes).putByte('\r').putByte('\n')
    )
    builder.result()
  }

}
