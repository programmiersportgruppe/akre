package org.programmiersportgruppe.redis

import scala.concurrent.Future

import org.programmiersportgruppe.redis.Command._


object Command {
  type Argument = RScalar with RSuccessValue

  case class Name(override val toString: String) {
    val asRSimpleStrings: Seq[RSimpleString] = toString.split(" ").toSeq.map(RSimpleString(_))
  }
}

trait Command {
  val name: Name
  val arguments: Seq[Argument]

  def nameAndArguments: Seq[Argument] = name.asRSimpleStrings ++ arguments
  def asRArray: RArray = RArray(nameAndArguments.map(_.asRBulkString))
  def asCliString: String = nameAndArguments.mkString(" ")

  def execute(implicit redis: RedisAsync): Future[RSuccessValue] = redis.execute(this)
}

case class UntypedCommand(name: Name, arguments: Seq[Argument]) extends Command {

  override def toString = "UntypedCommand: " + asCliString
}

object UntypedCommand {
  def apply(name: String, arguments: Argument*): UntypedCommand = new UntypedCommand(Name(name), arguments)
}
