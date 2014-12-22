package org.programmiersportgruppe.redis

import akka.util.ByteString

import scala.concurrent.Future

import org.programmiersportgruppe.redis.Command._


object Command {

  case class Name(override val toString: String) {
    val asConstants: Seq[Constant] = toString.split(" ").toSeq.map(Constant)
  }

  object Name {
    def apply(byteString: ByteString): Name = Name(byteString.utf8String)
  }

}

trait Command {
  val name: Name
  val arguments: Seq[ByteString]

  def nameAndArguments: Seq[ByteString] = name.asConstants.map(_.asByteString) ++ arguments

  def asCliString: String = nameAndArguments.mkString(" ")

  def execute(implicit redis: RedisAsync): Future[RSuccessValue] = redis.execute(this)
}

case class UntypedCommand(name: Name, arguments: Seq[ByteString]) extends Command {
  override def toString = "UntypedCommand: " + asCliString
}

object UntypedCommand {

  import org.programmiersportgruppe.redis.CommandArgument.ImplicitConversions._

  def apply(name: String, arguments: CommandArgument*): UntypedCommand =
    new UntypedCommand(Name(name), arguments.map(_.asByteString))

  def fromRValue(arguments: Seq[RValue]): Option[UntypedCommand] = {
    val stringArguments = arguments.map {
      case RBulkString(Some(arg)) => arg
      case _ => ???
    }
    stringArguments match {
      case name +: args => Some(new UntypedCommand(Name(name), args))
      case _ => ???
    }
  }

}
