package org.programmiersportgruppe.redis

import scala.concurrent.{ExecutionContext, Future}

import akka.util.ByteString


/**
 * @define timeoutExplanation
 */
trait RedisAsync {
  implicit val executor: ExecutionContext

  /**
   * Executes a command, returning a future reply from the server.
   *
   * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
   *
   * $timeoutExplanation
   */
  def execute(command: Command): Future[RSuccessValue]

  /**
   * Executes a command that is expected to cause the server to close the connection,
   * returning a unit future that is completed when the connection is closed.
   *
   * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
   *
   * If the server sends any reply other than an error, a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $timeoutExplanation
   */
  def executeConnectionClose(command: Command): Future[Unit]

  /**
   * Executes a command and extracts an optional [[akka.util.ByteString]] from the expected [[RBulkString]] reply.
   *
   * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
   *
   * If the server sends any reply other than a bulk string or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $timeoutExplanation
   */
  def executeByteString(command: Command): Future[Option[ByteString]] =
    execute(command) map {
      case RBulkString(data) => data
      case reply             => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and extracts an optional String from the expected UTF-8 encoded [[RBulkString]] reply.
   *
   * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
   *
   * If the server sends any reply other than a bulk string or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $timeoutExplanation
   */
  def executeString(command: Command): Future[Option[String]] =
    execute(command) map {
      case RBulkString(data) => data.map(_.utf8String)
      case reply             => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and extracts a Long from the expected [[RInteger]] reply.
   *
   * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
   *
   * If the server sends any reply other than an integer or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $timeoutExplanation
   */
  def executeLong(command: Command): Future[Long] =
    execute(command) map {
      case RInteger(value) => value
      case reply           => throw new UnexpectedReplyException(command, reply)
    }

  /**
   * Executes a command and verifies that it gets an "OK" status reply.
   *
   * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
   *
   * If the server sends any reply other than an "OK" status or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $timeoutExplanation
   */
  def executeSuccessfully(command: Command): Future[Unit] =
    execute(command) map {
      case RSimpleString.OK => ()
      case reply            => throw new UnexpectedReplyException(command, reply)
    }
}
