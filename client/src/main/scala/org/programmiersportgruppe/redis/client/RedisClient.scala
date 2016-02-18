package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._

import akka.actor.ActorRefFactory
import akka.routing._
import akka.util.Timeout

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.CLIENT_SETNAME


/**
 * @define timeoutExplanation
 *         If the connection pool fails to deliver a reply within the `requestTimeout`,
 *         a failed future containing an [[akka.pattern.AskTimeoutException]] will be returned.
 */
class RedisClient(actorRefFactory: ActorRefFactory, serverAddress: InetSocketAddress, connectTimeout: FiniteDuration, requestTimeout: Timeout, numberOfConnections: Int, connectionSetupCommands: Seq[Command] = Nil, poolActorName: String = "akre-redis-pool") extends RedisAsync {
  def this(actorRefFactory: ActorRefFactory, serverAddress: InetSocketAddress, connectTimeout: FiniteDuration, requestTimeout: Timeout, numberOfConnections: Int, initialClientName: String, poolActorName: String) =
    this(actorRefFactory, serverAddress, connectTimeout, requestTimeout, numberOfConnections, Seq(CLIENT_SETNAME(initialClientName)), poolActorName)

  import akka.pattern.ask

  override implicit val executor = actorRefFactory.dispatcher
  implicit private val timeout = requestTimeout

  private val poolActor = {
    val connection = RedisCommandReplyActor.props(serverAddress, connectionSetupCommands, Some(Ready))

    val pool = ResilientPool.props(
      childProps = connection,
      size = numberOfConnections,
      creationCircuitBreakerSettings = CircuitBreakerSettings(
        consecutiveFailureTolerance = 2,
        openDurationProgression = DurationProgression.doubling(100.milliseconds, 1.minute),
        halfOpenTimeout = connectTimeout
      ),
      routingLogic = RoundRobinRoutingLogic()
    )

    actorRefFactory.actorOf(pool, poolActorName)
  }

  def waitUntilConnected(timeout: FiniteDuration, minConnections: Int = 1, queryInterval: FiniteDuration = 10.millis, queryTolerance: FiniteDuration = 10.millis): Unit = {
    require(minConnections <= numberOfConnections)
    val deadline = timeout.fromNow

    while ({
      val queryTimeout = (deadline.timeLeft max Duration.Zero) + queryTolerance
      val Routees(routees) = Await.result(poolActor.ask(GetRoutees)(queryTimeout), queryTimeout)
      routees.length < minConnections
    }) {
      val timeLeft = deadline.timeLeft
      if (timeLeft < Duration.Zero)
        throw new TimeoutException(s"Exceeded $timeout timeout while waiting for at least $minConnections connections")
      Thread.sleep((queryInterval min timeLeft).toMillis)
    }
  }

  override def execute(command: Command): Future[RSuccessValue] = (poolActor ? command).transform({
    case (`command`, r: RSuccessValue) => r
    case (`command`, e: RError)        => throw new ErrorReplyException(command, e)
  }, {
    case e: Throwable => new RequestExecutionException(command, e)
  })

  override def executeConnectionClose(command: Command): Future[Unit] = (poolActor ? command).transform({
    case ()    => ()
    case (`command`, r: RSuccessValue) => throw new UnexpectedReplyException(command, r)
    case (`command`, e: RError)        => throw new ErrorReplyException(command, e)
  }, {
    case e: Throwable => new RequestExecutionException(command, e)
  })

  /**
   * Stops the connection pool used by the client,
   * returning a unit future that is completed when the pool is stopped.
   *
   * If the connection pool fails to deliver a reply within 30 seconds,
   * a failed future containing an [[akka.pattern.AskTimeoutException]] will be returned.
   */
  def shutdown(): Future[Unit] = {
    akka.pattern.gracefulStop(poolActor, 30.seconds).map(_ => ())
  }

//  def executeBoolean(command: RedisCommand[IntegerReply]): Future[Boolean] = executeAny(command) map { case IntegerReply(0) => false; case IntegerReply(1) => true }
//  def executeBytes(command: RedisCommand[BulkReply]): Future[Option[ByteString]] = executeAny(command) map { case BulkReply(data) => data }
//  def executeString(command: RedisCommand[BulkReply]): Future[Option[String]] = executeAny(command) map { case BulkReply(data) => data.map(_.utf8String) }
}
