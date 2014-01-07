package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import scala.concurrent.Future
import akka.util.{ByteString, Timeout}
import akka.actor.ActorSystem
import akka.routing.RoundRobinPool

case class ErrorReplyException(command: Command, reply: ErrorReply)
  extends Exception(s"Error reply received: ${reply.error}\nFor command: $command\nSent as: ${command.serialised.utf8String}")

class RedisClient(actorSystem: ActorSystem, serverAddress: InetSocketAddress, requestTimeout: Timeout, numberOfConnections: Int) {
  import akka.pattern.ask
  import actorSystem.dispatcher

  implicit val timeout = requestTimeout

  val routerActor = actorSystem.actorOf(RoundRobinPool(numberOfConnections).props(RedisConnectionActor.props(serverAddress)), "redis-connection-pool")

  def execute(command: Command): Future[Any] = (routerActor ? command).map {
    case (`command`, e: ErrorReply) => throw new ErrorReplyException(command, e)
    case (`command`, reply) => reply
  }

  def executeByteString(command: Command with BulkExpected): Future[Option[ByteString]] =
    execute(command) map { case BulkReply(data) => data }

  def executeString(command: Command with BulkExpected): Future[Option[String]] =
    execute(command) map { case BulkReply(data) => data.map(_.utf8String) }

//  def executeForReply(command: RedisCommand): Future[Reply] = execute(command).mapTo[Reply]

  def executeInt(command: Command with IntegerExpected): Future[Int] =
    execute(command) map { case IntegerReply(value) => value }

//  def executeBoolean(command: RedisCommand[IntegerReply]): Future[Boolean] = executeAny(command) map { case IntegerReply(0) => false; case IntegerReply(1) => true }
//  def executeBytes(command: RedisCommand[BulkReply]): Future[Option[ByteString]] = executeAny(command) map { case BulkReply(data) => data }
//  def executeString(command: RedisCommand[BulkReply]): Future[Option[String]] = executeAny(command) map { case BulkReply(data) => data.map(_.utf8String) }
}
