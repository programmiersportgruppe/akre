package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress
import scala.concurrent.duration._

import akka.actor.{ ActorRef, ActorRefFactory, Props }
import akka.routing.RoundRobinRoutingLogic

import org.programmiersportgruppe.redis.client.ConnectionPoolSettings._


object ConnectionPoolSettings {

  val defaultConnectionEstablishmentSettings =
    CircuitBreakerSettings(
      consecutiveFailureTolerance = 2,
      openDurationProgression = DurationProgression.doubling(100.milliseconds, 1.minute),
      halfOpenTimeout = 5.seconds // connect timeout
    )

}

/** Settings for a connection pool
  *
  * @param serverAddress                   address of the server to connect to
  * @param size                            the number of connections to hold in the pool
  * @param connectionEstablishmentSettings settings for the circuit breaker controlling connection establishment;
  *                                        the `halfOpenTimeout` is effectively be the connect timeout
  */
case class ConnectionPoolSettings(
    serverAddress: InetSocketAddress,
    size: Int = 3,
    connectionEstablishmentSettings: CircuitBreakerSettings = defaultConnectionEstablishmentSettings
) {

  /** Create a [[ResilientPoolActor]] of connections with these settings
    *
    * @param actorRefFactory used to create the pool
    * @param actorName       name of the pool actor
    * @param childProps      derives the [[akka.actor.Props]] from which individual connections are created from the server address
    */
  def createResilientPool(actorRefFactory: ActorRefFactory, actorName: String)(childProps: InetSocketAddress => Props): ActorRef =
    actorRefFactory.actorOf(
      ResilientPoolActor.props(
        size = size,
        childProps = childProps(serverAddress),
        creationCircuitBreakerSettings = connectionEstablishmentSettings,
        routingLogic = RoundRobinRoutingLogic()
      ),
      name = actorName
    )

}
