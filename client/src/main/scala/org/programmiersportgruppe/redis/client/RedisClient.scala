package org.programmiersportgruppe.redis.client

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorRefFactory
import akka.util.Timeout

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.CLIENT_SETNAME


class RedisClient(
  val connectionPoolSettings: ConnectionPoolSettings,
  val requestTimeout: Timeout,
  actorRefFactory: ActorRefFactory,
  connectionSetupCommands: Seq[Command] = Nil,
  poolActorName: String = "redis-client-connection-pool"
) extends RedisAsync {
  def this(connectionPoolSettings: ConnectionPoolSettings, requestTimeout: Timeout, actorRefFactory: ActorRefFactory, initialClientName: String) =
    this(connectionPoolSettings, requestTimeout, actorRefFactory, Seq(CLIENT_SETNAME(initialClientName)))

  import akka.pattern.ask

  implicit private[this] final def timeout = requestTimeout

  private[this] val poolActor =
    connectionPoolSettings.createResilientPool(actorRefFactory, poolActorName) { serverAddress =>
      RedisCommandReplyActor.props(serverAddress, connectionSetupCommands, Some(ResilientPoolActor.ChildReady))
    }

  def waitUntilConnected(timeout: FiniteDuration, minConnections: Int = 1, pollingInterval: FiniteDuration = 10.millis, queryTolerance: FiniteDuration = 10.millis): Unit = {
    require(minConnections <= connectionPoolSettings.size, s"No point waiting for $minConnections connections when pool only has size ${connectionPoolSettings.size}")
    ResilientPoolActor.waitForActiveChildren(poolActor, timeout, minConnections, pollingInterval, queryTolerance)
  }

  override def execute(command: Command): Future[RSuccessValue] = (poolActor ? command).transform({
    case (`command`, r: RSuccessValue) => r
    case (`command`, e: RError)        => throw new ErrorReplyException(command, e)
  }, {
    case e: Throwable => new RequestExecutionException(command, e)
  })(ImmediateExecutionOnCallingThread)

  override def executeConnectionClose(command: Command): Future[Unit] = (poolActor ? command).transform({
    case ()                            => ()
    case (`command`, r: RSuccessValue) => throw new UnexpectedReplyException(command, r)
    case (`command`, e: RError)        => throw new ErrorReplyException(command, e)
  }, {
    case e: Throwable => new RequestExecutionException(command, e)
  })(ImmediateExecutionOnCallingThread)

  /**
   * Stops the connection pool used by the client,
   * returning a unit future that is completed when the pool is stopped.
   *
   * If the connection pool fails to deliver a reply within 30 seconds,
   * a failed future containing an [[akka.pattern.AskTimeoutException]] will be returned.
   */
  def shutdown(): Future[Unit] = {
    akka.pattern.gracefulStop(poolActor, 30.seconds)
      .map(_ => ())(ImmediateExecutionOnCallingThread)
  }

//  def executeBoolean(command: RedisCommand[IntegerReply]): Future[Boolean] = executeAny(command) map { case IntegerReply(0) => false; case IntegerReply(1) => true }
//  def executeBytes(command: RedisCommand[BulkReply]): Future[Option[ByteString]] = executeAny(command) map { case BulkReply(data) => data }
//  def executeString(command: RedisCommand[BulkReply]): Future[Option[String]] = executeAny(command) map { case BulkReply(data) => data.map(_.utf8String) }
}
