package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import akka.util.{ByteString, Timeout}
import akka.actor.ActorSystem

case class ErrorReplyException(command: Command, reply: ErrorReply)
  extends Exception(s"Error reply received: ${reply.error}\nFor command: $command\nSent as: ${command.serialised.utf8String}")

class RedisClient(actorSystem: ActorSystem, serverAddress: InetSocketAddress, requestTimeout: Timeout) {
  import akka.pattern.ask
  import actorSystem.dispatcher
  import RedisConnectionActor._
  import scala.concurrent.duration._

  implicit val timeout = requestTimeout

  val connectionActor = actorSystem.actorOf(RedisConnectionActor.props(serverAddress), "redis-connection")

  def execute(command: Command): Future[Any] = (connectionActor ? command).map {
    case (`command`, e: ErrorReply) => throw new ErrorReplyException(command, e)
    case (`command`, reply) => reply
  }

  def executeByteString(command: Command with BulkExpected): Future[Option[ByteString]] =
    execute(command) map { case BulkReply(data) => data }

  def executeString(command: Command with BulkExpected): Future[Option[String]] =
    execute(command) map { case BulkReply(data) => data.map(_.utf8String) }

//  def executeForReply(command: RedisCommand): Future[Reply] = execute(command).mapTo[Reply]
//  def executeInt(command: RedisCommand[IntegerReply]): Future[Int] = executeAny(command) map { case IntegerReply(i) => i }
//  def executeBoolean(command: RedisCommand[IntegerReply]): Future[Boolean] = executeAny(command) map { case IntegerReply(0) => false; case IntegerReply(1) => true }
//  def executeBytes(command: RedisCommand[BulkReply]): Future[Option[ByteString]] = executeAny(command) map { case BulkReply(data) => data }
//  def executeString(command: RedisCommand[BulkReply]): Future[Option[String]] = executeAny(command) map { case BulkReply(data) => data.map(_.utf8String) }
}
