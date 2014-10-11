package org.programmiersportgruppe.redis.commands

import scala.concurrent.Future

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.Command.Argument


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
sealed abstract class RecognisedCommand(override val arguments: Argument*) extends Command {
  override val name = Command.Name(getClass.getSimpleName.replace('_', ' '))

  override def toString = "RecognisedCommand: " + asCliString
}


// Keys
case class DEL(key: Key, additionalKeys: Key*) extends RecognisedCommand(key +: additionalKeys: _*) with IntegerExpected
case class DUMP(key: Key) extends RecognisedCommand(key) with BulkExpected
case class EXISTS(key: Key) extends RecognisedCommand(key) with BooleanIntegerExpected
case class EXPIRE(key: Key, seconds: Long) extends RecognisedCommand(key, RInteger(seconds)) with BooleanIntegerExpected

// Strings
case class APPEND(key: Key, value: ByteString) extends RecognisedCommand(key, RBulkString(value)) with IntegerExpected
case class GET(key: Key) extends RecognisedCommand(key) with BulkExpected

sealed abstract class ExpirationPolicy(flag: String, duration: Long) { val args = Seq(RSimpleString(flag), RInteger(duration)) }
case class ExpiresInSeconds(seconds: Long) extends ExpirationPolicy("EX", seconds)
case class ExpiresInMilliseconds(millis: Long) extends ExpirationPolicy("PX", millis)

sealed abstract class CreationRestriction(flag: String) { val arg = RSimpleString(flag) }
case object OnlyIfKeyDoesNotAlreadyExist extends CreationRestriction("NX")
case object OnlyIfKeyAlreadyExists extends CreationRestriction("XX")

case class SET(key: Key, value: ByteString, expiration: Option[ExpirationPolicy] = None, restriction: Option[CreationRestriction] = None) extends RecognisedCommand(Seq(key, RBulkString(value)) ++ expiration.toSeq.flatMap(_.args) ++ restriction.map(_.arg): _*)

// Server
case class CLIENT_SETNAME(connectionName: String) extends RecognisedCommand(RSimpleString(connectionName)) with OkStatusExpected

sealed abstract class PersistenceModifier(flag: String) { val arg = RSimpleString(flag) }
case object Save extends PersistenceModifier("SAVE")
case object NoSave extends PersistenceModifier("NOSAVE")

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
