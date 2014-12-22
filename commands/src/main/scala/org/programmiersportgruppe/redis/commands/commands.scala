package org.programmiersportgruppe.redis.commands

import akka.util.ByteString
import org.programmiersportgruppe.redis.Command.Name
import org.programmiersportgruppe.redis.CommandArgument.ImplicitConversions._
import org.programmiersportgruppe.redis._

import scala.concurrent.Future
import scala.util.Try


trait BulkExpected {
  self: Command =>
  def executeByteString(implicit redis: RedisAsync): Future[Option[ByteString]] = redis.executeByteString(this)
  def executeString(implicit redis: RedisAsync): Future[Option[String]] = redis.executeString(this)
}
trait IntegerExpected {
  self: Command =>
  def executeLong(implicit redis: RedisAsync): Future[Long] = redis.executeLong(this)
}
trait OkStatusExpected {
  self: Command =>
  def executeSuccessfully(implicit redis: RedisAsync): Future[Unit] = redis.executeSuccessfully(this)
}
trait ConnectionCloseExpected {
  self: Command =>
  def executeConnectionClose(implicit redis: RedisAsync): Future[Unit] = redis.executeConnectionClose(this)
}
trait BooleanIntegerExpected

//sealed abstract class IntCommand(args: Seq[RedisCommandArgument]) extends DocumentedRedisCommand[IntegerReply](args)
//sealed abstract class BooleanCommand(args: Seq[RedisCommandArgument]) extends DocumentedRedisCommand[IntegerReply](args)
//sealed abstract class BulkCommand(args: Seq[RedisCommandArgument]) extends DocumentedRedisCommand[BulkReply](args)


// TODO: Rename class
sealed abstract class RecognisedCommand(val typedArguments: CommandArgument*) extends Command {
  override val name = Command.Name(getClass.getSimpleName.replace('_', ' '))
  override val arguments = typedArguments.map(_.asByteString)

  override def toString = "RecognisedCommand: " + typedArguments.mkString(name.toString + " ", " ", "")
}

object RecognisedCommand {
  def fromUntypedCommand(untyped: UntypedCommand): Option[RecognisedCommand] =
    Try {
      untyped match {
        case UntypedCommand(Name("GET"), Seq(key)) => GET(Key(key))
        case UntypedCommand(Name("SET"), Seq(key, value)) => SET(Key(key), value.asByteString)
        case UntypedCommand(Name("DEL"), head +: tail) => DEL(Key(head), tail.map(Key(_)): _*)
      }
    }.toOption
}

// Keys
case class DEL(key: Key, additionalKeys: Key*) extends RecognisedCommand(key +: additionalKeys: _*) with IntegerExpected
case class DUMP(key: Key) extends RecognisedCommand(key) with BulkExpected
case class EXISTS(key: Key) extends RecognisedCommand(key) with BooleanIntegerExpected
case class EXPIRE(key: Key, seconds: Long) extends RecognisedCommand(key, seconds) with BooleanIntegerExpected

// Strings
case class APPEND(key: Key, value: ByteString) extends RecognisedCommand(key, value) with IntegerExpected
case class GET(key: Key) extends RecognisedCommand(key) with BulkExpected
case class GETRANGE(key: Key, start: Long, end: Long) extends RecognisedCommand(key, start, end) with BulkExpected

sealed abstract class ExpirationPolicy(flag: Constant, duration: Long) { val args = Seq[CommandArgument](flag, duration) }
case class ExpiresInSeconds(seconds: Long) extends ExpirationPolicy(Constant("EX"), seconds)
case class ExpiresInMilliseconds(millis: Long) extends ExpirationPolicy(Constant("PX"), millis)

sealed abstract class CreationRestriction(flag: Constant) { val arg = flag }
case object OnlyIfKeyDoesNotAlreadyExist extends CreationRestriction(Constant("NX"))
case object OnlyIfKeyAlreadyExists extends CreationRestriction(Constant("XX"))

case class SET(key: Key, value: ByteString, expiration: Option[ExpirationPolicy] = None, restriction: Option[CreationRestriction] = None) extends RecognisedCommand(Seq(key, StringArgument(value)) ++ expiration.toSeq.flatMap(_.args) ++ restriction.map(_.arg): _*)

// Server
case class CLIENT_SETNAME(connectionName: String) extends RecognisedCommand(connectionName) with OkStatusExpected

sealed abstract class PersistenceModifier(flag: Constant) { val arg = flag }
case object Save extends PersistenceModifier(Constant("SAVE"))
case object NoSave extends PersistenceModifier(Constant("NOSAVE"))

case class SHUTDOWN(forcePersistence: Option[PersistenceModifier] = None) extends RecognisedCommand(forcePersistence.map(_.arg).toSeq: _*) with ConnectionCloseExpected


// Transactions
case class Multi(command1: Command, command2: Command, additionalCommands: Command*)

trait OptimisticTransactionContinuation
trait OptimisticTransactionPerpetuation extends OptimisticTransactionContinuation {
  def execute(continue: Seq[RValue] => OptimisticTransactionContinuation)(implicit redis: RedisAsync) = ???
}
case class Watching(key: Key, additionalKeys: Key*)(command: Command, additionalCommands: Command*) extends OptimisticTransactionPerpetuation
case class Continue(command: Command, additionalCommands: Command*) extends OptimisticTransactionPerpetuation
case class Commit(command: Command, additionalCommands: Command*) extends OptimisticTransactionContinuation {
  def execute(implicit redis: RedisAsync) = ???
}

/*

Assuming transactions cannot be nested:

  BaseExecutable // abstract, ?
    Pipelinable // abstract, public
      BaseCommand // abstract, internal
        AtomicCommand // safe
        CoordinationCommand // unsafe
      Transaction(AtomicCommand{2,}) // safe
    OptimisticTransaction(AtomicCommand+, Transaction) // safe

*/
