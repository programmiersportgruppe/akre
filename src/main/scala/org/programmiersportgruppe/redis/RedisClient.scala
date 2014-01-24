package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import akka.routing.RoundRobinRouter
import akka.util.{ByteString, Timeout}


case class ErrorReplyException(command: Command, reply: ErrorReply)
  extends Exception(s"Error reply received: ${reply.error}\nFor command: $command\nSent as: ${command.serialised.utf8String}")

class RedisClient(actorSystem: ActorSystem, serverAddress: InetSocketAddress, requestTimeout: Timeout, numberOfConnections: Int) {
  import akka.pattern.ask
  import actorSystem.dispatcher

  implicit val timeout = requestTimeout

  val routerActor = actorSystem.actorOf(RedisConnectionActor.props(serverAddress).withRouter(RoundRobinRouter(numberOfConnections)), "redis-connection-pool")

  def execute(command: Command): Future[Any] = (routerActor ? command).map {
    case (`command`, e: ErrorReply) => throw new ErrorReplyException(command, e)
    case (`command`, reply) => reply
  }

  def executeByteString(command: Command with BulkExpected): Future[Option[ByteString]] =
    execute(command) map { case BulkReply(data) => data }

  def executeString(command: Command with BulkExpected): Future[Option[String]] =
    execute(command) map { case BulkReply(data) => data.map(_.utf8String) }

//  def executeForReply(command: RedisCommand): Future[Reply] = execute(command).mapTo[Reply]

  def executeLong(command: Command with IntegerExpected): Future[Long] =
    execute(command) map { case IntegerReply(value) => value }

  def shutdown(): Future[Boolean] = {
    akka.pattern.gracefulStop(routerActor, FiniteDuration(5, TimeUnit.SECONDS))
  }

//  def executeBoolean(command: RedisCommand[IntegerReply]): Future[Boolean] = executeAny(command) map { case IntegerReply(0) => false; case IntegerReply(1) => true }
//  def executeBytes(command: RedisCommand[BulkReply]): Future[Option[ByteString]] = executeAny(command) map { case BulkReply(data) => data }
//  def executeString(command: RedisCommand[BulkReply]): Future[Option[String]] = executeAny(command) map { case BulkReply(data) => data.map(_.utf8String) }
}
