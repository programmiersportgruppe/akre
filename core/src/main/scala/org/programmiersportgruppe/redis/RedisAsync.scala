package org.programmiersportgruppe.redis

import scala.concurrent.{ExecutionContext, Future}

import akka.pattern.AskTimeoutException
import akka.util.ByteString


trait RedisAsync {
  implicit val executor: ExecutionContext

  def execute(command: Command): Future[RSuccessValue]

  /**
   * Executes a command that is expected to cause the server to close the connection.
   *
   * @param command the command to be executed
   * @return a unit future that completes when the connection closes
   */
  def executeConnectionClose(command: Command): Future[Unit]

  /**
   * Executes a command and extracts an optional akka.util.ByteString from the bulk reply that is expected.
   *
   * @param command the bulk reply command to be executed
   * @throws ErrorReplyException      if the server gives an error reply
   * @throws AskTimeoutException      if the connection pool fails to deliver a reply within the requestTimeout
   * @throws UnexpectedReplyException if the server gives a proper non-bulk reply
   */
  def executeByteString(command: Command): Future[Option[ByteString]] =
    execute(command) map {
      case RBulkString(data) => data
      case reply             => throw new UnexpectedReplyException(command, reply)
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
  def executeString(command: Command): Future[Option[String]] =
    execute(command) map {
      case RBulkString(data) => data.map(_.utf8String)
      case reply             => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and extracts a Long from the int reply that is expected.
   *
   * @param command the int reply command to be executed
   * @throws ErrorReplyException      if the server gives an error reply
   * @throws AskTimeoutException      if the connection pool fails to deliver a reply within the requestTimeout
   * @throws UnexpectedReplyException if the server gives a proper non-bulk reply
   */
  def executeLong(command: Command): Future[Long] =
    execute(command) map {
      case RInteger(value) => value
      case reply           => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and verifies that it gets an "OK" status reply.
   *
   * @param command the ok status reply command to be executed
   * @throws ErrorReplyException      if the server gives an error reply
   * @throws AskTimeoutException      if the connection pool fails to deliver a reply within the requestTimeout
   * @throws UnexpectedReplyException if the server gives a proper reply that is not StatusReply("OK")
   */
  def executeSuccessfully(command: Command): Future[Unit] =
    execute(command) map {
      case RSimpleString.OK => ()
      case reply            => throw new UnexpectedReplyException(command, reply)
    }
}
