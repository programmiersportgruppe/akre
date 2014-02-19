package org.programmiersportgruppe.redis

import akka.util.{ByteStringBuilder, ByteString}
import scala.concurrent.{ExecutionContext, Future}


sealed trait CommandArgument { val toByteString: ByteString }
case class Key(key: ByteString) extends CommandArgument {
  override lazy val toByteString = key
  override def toString = s"Key(${key.utf8String})"
}
object Key { def apply(key: String) = new Key(ByteString(key)) }
case class RByteString(value: ByteString) extends CommandArgument { override lazy val toByteString = value }
case class RString(value: String) extends CommandArgument { override lazy val toByteString = ByteString(value) }
case class RInteger(value: Long) extends CommandArgument { override lazy val toByteString = ByteString(value.toString) }


sealed abstract class Command(commandName: String, args: Seq[CommandArgument]) {

  val name = commandName
  lazy val argsWithCommand = RString(name) +: args

  def execute(implicit client: RedisClient) = client.execute(this)

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

//case class UntypedRedisCommand(name: String, args: Seq[RedisCommandArgument]) extends RedisCommand(name, args)

sealed abstract class NamedCommand(args: CommandArgument*) extends Command(null, args) {
  override val name = this.getClass.getSimpleName
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

// Keys
case class DEL(key: Key, additionalKeys: Key*) extends NamedCommand(key +: additionalKeys: _*) with IntegerExpected
case class DUMP(key: Key) extends NamedCommand(key) with BulkExpected
case class EXISTS(key: Key) extends NamedCommand(key) with BooleanIntegerExpected
case class EXPIRE(key: Key, seconds: Long) extends NamedCommand(key, RInteger(seconds)) with BooleanIntegerExpected

// Strings
case class GET(key: Key) extends NamedCommand(key) with BulkExpected

sealed abstract class ExpirationPolicy(flag: String, duration: Long) { val args = Seq(RString(flag), RInteger(duration)) }
case class ExpiresInSeconds(seconds: Long) extends ExpirationPolicy("EX", seconds)
case class ExpiresInMilliseconds(millis: Long) extends ExpirationPolicy("PX", millis)

sealed abstract class CreationRestriction(flag: String) { val arg = RString(flag) }
case object OnlyIfKeyDoesNotAlreadyExist extends CreationRestriction("NX")
case object OnlyIfKeyAlreadyExists extends CreationRestriction("XX")

case class SET(key: Key, value: ByteString, expiration: Option[ExpirationPolicy] = None, restriction: Option[CreationRestriction] = None) extends NamedCommand(Seq(key, RByteString(value)) ++ expiration.toSeq.flatMap(_.args) ++ restriction.map(_.arg): _*)

// Server
sealed abstract class PersistenceModifier(flag: String) { val arg = RString(flag) }
case object Save extends PersistenceModifier("SAVE")
case object NoSave extends PersistenceModifier("NOSAVE")

case class SHUTDOWN(forcePersistence: Option[PersistenceModifier] = None) extends NamedCommand(forcePersistence.map(_.arg).toSeq: _*) with ConnectionCloseExpected


// Transactions
case class Multi(command1: Command, command2: Command, additionalCommands: Command*)

trait OptimisticTransactionContinuation
trait OptimisticTransactionPerpetuation extends OptimisticTransactionContinuation {
  def execute(continue: Seq[Reply] => OptimisticTransactionContinuation)(implicit client: RedisClient) = ???
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
