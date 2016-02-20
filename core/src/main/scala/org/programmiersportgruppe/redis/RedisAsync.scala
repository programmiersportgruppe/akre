package org.programmiersportgruppe.redis

import scala.concurrent.{ExecutionContext, Future}

import akka.util.ByteString


/** An interface to asynchronously execute Redis commands
  *
  * @define errorReplyException
  * If the server responds with an [[RError]], a failed future containing an [[ErrorReplyException]] will be returned.
  * @define askTimeoutException
  * If the connection pool fails to deliver a reply within the `requestTimeout`,
  * a failed future containing an [[akka.pattern.AskTimeoutException]] will be returned.
  */
trait RedisAsync {
  implicit val executor: ExecutionContext

  /**
   * Executes a command, returning a future reply from the server.
   *
   * $errorReplyException
   *
   * $askTimeoutException
   */
  def execute(command: Command): Future[RSuccessValue]

  /**
   * Executes a command that is expected to cause the server to close the connection,
   * returning a unit future that is completed when the connection is closed.
   *
   * $errorReplyException
   *
   * If the reply is anything other than an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $askTimeoutException
   */
  def executeConnectionClose(command: Command): Future[Unit]

  /**
    * Executes a command and extracts a value from the reply
    *
    * $errorReplyException
    *
    * If `extractValue` is undefined for the reply,
    * a failed future containing an [[UnexpectedReplyException]] will be returned.
    *
    * @param extractValue a partial function that extracts a value from a success reply
    * @tparam A the type of value returned
    */
  private def executeAndExtract[A](command: Command)(extractValue: PartialFunction[RSuccessValue, A]): Future[A] =
    execute(command).map { successReply =>
      extractValue.applyOrElse(successReply, (r: RSuccessValue) => throw new UnexpectedReplyException(command, r))
    }

  /**
   * Executes a command and extracts an optional [[akka.util.ByteString]] from the expected [[RBulkString]] reply.
   *
   * $errorReplyException
   *
   * If the reply is anything other than a bulk string or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $askTimeoutException
   */
  def executeByteString(command: Command): Future[Option[ByteString]] =
    executeAndExtract(command) {
      case RBulkString(data) => data
    }

  /**
   * Executes a command and extracts an optional String from the expected UTF-8 encoded [[RBulkString]] reply.
   *
   * $errorReplyException
   *
   * If the reply is anything other than a bulk string or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $askTimeoutException
   */
  def executeString(command: Command): Future[Option[String]] =
    executeAndExtract(command) {
      case RBulkString(data) => data.map(_.utf8String)
    }

  /**
   * Executes a command and extracts a Long from the expected [[RInteger]] reply.
   *
   * $errorReplyException
   *
   * If reply is anything other than an integer or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $askTimeoutException
   */
  def executeLong(command: Command): Future[Long] =
    executeAndExtract(command) {
      case RInteger(value) => value
    }

  /**
   * Executes a command and verifies that it gets an "OK" status reply.
   *
   * $errorReplyException
   *
   * If reply is anything other than an "OK" status or an error,
   * a failed future containing an [[UnexpectedReplyException]] will be returned.
   *
   * $askTimeoutException
   */
  def executeSuccessfully(command: Command): Future[Unit] =
    executeAndExtract(command) {
      case RSimpleString.OK => ()
    }
}
