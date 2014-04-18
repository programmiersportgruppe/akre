package org.programmiersportgruppe.redis

import akka.util.{ByteStringBuilder, ByteString}
import scala.concurrent.{ExecutionContext, Future}
import Command.{Argument, Key}

abstract class TypedCommand[T] {
  self: Command =>
  def extractResult(reply: RValue): T
}


trait BulkExpected {
  self: Command =>
  def executeByteString(implicit client: RedisClient): Future[Option[ByteString]] = client.executeByteString(this)
  def executeString(implicit client: RedisClient): Future[Option[String]] = client.executeString(this)
}
trait IntegerExpected {
  self: Command =>
  def executeLong(implicit client: RedisClient): Future[Long] = client.executeLong(this)
}
trait OkStatusExpected {
  self: Command =>
  def executeSuccessfully(implicit client: RedisClient): Future[Unit] = client.executeSuccessfully(this)
}
trait ConnectionCloseExpected {
  self: Command =>
  def executeConnectionClose(implicit client: RedisClient): Future[Unit] = client.executeConnectionClose(this)
}
trait BooleanIntegerExpected

//sealed abstract class IntCommand(args: Seq[RedisCommandArgument]) extends DocumentedRedisCommand[IntegerReply](args)
//sealed abstract class BooleanCommand(args: Seq[RedisCommandArgument]) extends DocumentedRedisCommand[IntegerReply](args)
//sealed abstract class BulkCommand(args: Seq[RedisCommandArgument]) extends DocumentedRedisCommand[BulkReply](args)


sealed abstract class NamedCommand(args: Argument*) extends Command(null, args) {
  override val name = getClass.getSimpleName.replace('_', ' ')
}


// Keys
case class DEL(key: Key, additionalKeys: Key*) extends NamedCommand(key +: additionalKeys: _*) with IntegerExpected
case class DUMP(key: Key) extends NamedCommand(key) with BulkExpected
case class EXISTS(key: Key) extends NamedCommand(key) with BooleanIntegerExpected
case class EXPIRE(key: Key, seconds: Long) extends NamedCommand(key, RInteger(seconds)) with BooleanIntegerExpected

// Strings
case class GET(key: Key) extends NamedCommand(key) with BulkExpected

sealed abstract class ExpirationPolicy(flag: String, duration: Long) { val args = Seq(RSimpleString(flag), RInteger(duration)) }
case class ExpiresInSeconds(seconds: Long) extends ExpirationPolicy("EX", seconds)
case class ExpiresInMilliseconds(millis: Long) extends ExpirationPolicy("PX", millis)

sealed abstract class CreationRestriction(flag: String) { val arg = RSimpleString(flag) }
case object OnlyIfKeyDoesNotAlreadyExist extends CreationRestriction("NX")
case object OnlyIfKeyAlreadyExists extends CreationRestriction("XX")

case class SET(key: Key, value: ByteString, expiration: Option[ExpirationPolicy] = None, restriction: Option[CreationRestriction] = None) extends NamedCommand(Seq(key, RBulkString(value)) ++ expiration.toSeq.flatMap(_.args) ++ restriction.map(_.arg): _*)

// Server
case class CLIENT_SETNAME(connectionName: String) extends NamedCommand(RSimpleString(connectionName)) with OkStatusExpected

sealed abstract class PersistenceModifier(flag: String) { val arg = RSimpleString(flag) }
case object Save extends PersistenceModifier("SAVE")
case object NoSave extends PersistenceModifier("NOSAVE")

case class SHUTDOWN(forcePersistence: Option[PersistenceModifier] = None) extends NamedCommand(forcePersistence.map(_.arg).toSeq: _*) with ConnectionCloseExpected


// Transactions
case class Multi(command1: Command, command2: Command, additionalCommands: Command*)

trait OptimisticTransactionContinuation
trait OptimisticTransactionPerpetuation extends OptimisticTransactionContinuation {
  def execute(continue: Seq[RValue] => OptimisticTransactionContinuation)(implicit client: RedisClient) = ???
}
case class Watching(key: Key, additionalKeys: Key*)(command: Command, additionalCommands: Command*) extends OptimisticTransactionPerpetuation
case class Continue(command: Command, additionalCommands: Command*) extends OptimisticTransactionPerpetuation
case class Commit(command: Command, additionalCommands: Command*) extends OptimisticTransactionContinuation {
  def execute(implicit client: RedisClient) = ???
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
