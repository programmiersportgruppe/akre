package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ActorRefFactory, SupervisorStrategy, OneForOneStrategy, ActorSystem}
import akka.routing.RoundRobinPool
import akka.util.{ByteString, Timeout}


case class ErrorReplyException(command: Command, reply: ErrorReply)
  extends Exception(s"Error reply received: ${reply.error}\nFor command: $command\nSent as: ${command.serialised.utf8String}")

case class UnexpectedReplyException(command: Command, reply: ProperReply)
  extends Exception(s"Unexpected reply received: ${reply}\nFor command: $command")


class RedisClient(actorRefFactory: ActorRefFactory, serverAddress: InetSocketAddress, requestTimeout: Timeout, numberOfConnections: Int, poolName: String = "redis-connection-pool") {
  import akka.pattern.ask
  import actorRefFactory.dispatcher

  implicit private val timeout = requestTimeout

  private val poolActor = {

    val connection = RedisConnectionActor.props(serverAddress)

    val pool = RoundRobinPool(
      nrOfInstances = numberOfConnections,
      supervisorStrategy = OneForOneStrategy(3, 5.seconds)(SupervisorStrategy.defaultDecider)
    ).props(connection)

    actorRefFactory.actorOf(pool, poolName)
  }

  /**
   * Executes a command.
   *
   * @param command the command to be executed
   * @return a non-error reply from the server
   * @throws ErrorReplyException if the server gives an error reply
   * @throws AskTimeoutException if the connection pool fails to deliver a reply within the requestTimeout
   */
  def execute(command: Command): Future[ProperReply] = (poolActor ? command).map {
    case (`command`, r: ProperReply) => r
    case (`command`, e: ErrorReply) => throw new ErrorReplyException(command, e)
  }

  /**
   * Executes a command and extracts an optional akka.util.ByteString from the bulk reply that is expected.
   *
   * @param command the bulk reply command to be executed
   * @throws ErrorReplyException      if the server gives an error reply
   * @throws AskTimeoutException      if the connection pool fails to deliver a reply within the requestTimeout
   * @throws UnexpectedReplyException if the server gives a proper non-bulk reply
   */
  def executeByteString(command: Command with BulkExpected): Future[Option[ByteString]] =
    execute(command) map {
      case BulkReply(data)  => data
      case reply            => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and extracts an optional String from the UTF-8 encoded bulk reply that is
   * expected.
   *
   * @param command the bulk reply command to be executed
   * @throws ???                      if the reply cannot be decoded as UTF-8
   * @throws ErrorReplyException      if the server gives an error reply
   * @throws AskTimeoutException      if the connection pool fails to deliver a reply within the requestTimeout
   * @throws UnexpectedReplyException if the server gives a proper non-bulk reply
   */
  def executeString(command: Command with BulkExpected): Future[Option[String]] =
    execute(command) map {
      case BulkReply(data)  => data.map(_.utf8String)
      case reply            => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and extracts a Long from the int reply that is expected.
   *
   * @param command the int reply command to be executed
   * @throws ErrorReplyException      if the server gives an error reply
   * @throws AskTimeoutException      if the connection pool fails to deliver a reply within the requestTimeout
   * @throws UnexpectedReplyException if the server gives a proper non-bulk reply
   */
  def executeLong(command: Command with IntegerExpected): Future[Long] =
    execute(command) map {
      case IntegerReply(value)  => value
      case reply                => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Stops the connection pool used by the client.
   * @throws AskTimeoutException if the connection pool fails to stop within 30 seconds
   */
  def shutdown(): Future[Unit] = {
    akka.pattern.gracefulStop(poolActor, 30.seconds).map(_ => ())
  }

//  def executeBoolean(command: RedisCommand[IntegerReply]): Future[Boolean] = executeAny(command) map { case IntegerReply(0) => false; case IntegerReply(1) => true }
//  def executeBytes(command: RedisCommand[BulkReply]): Future[Option[ByteString]] = executeAny(command) map { case BulkReply(data) => data }
//  def executeString(command: RedisCommand[BulkReply]): Future[Option[String]] = executeAny(command) map { case BulkReply(data) => data.map(_.utf8String) }
}
