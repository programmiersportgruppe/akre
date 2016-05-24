package org.programmiersportgruppe.redis.test

import akka.actor.{Actor, ActorRef, ActorRefFactory, OneForOneStrategy, Props, SupervisorStrategy}
import akka.event.Logging

object ProxyingParent {

  def apply(childProps: Props, parentRelay: ActorRef, name: String)(implicit actorRefFactory: ActorRefFactory): ActorRef =
    actorRefFactory.actorOf(props(childProps, parentRelay), name)

  def props(childProps: Props, parentRelay: ActorRef): Props =
    Props(classOf[ProxyingParent], childProps, parentRelay)

}

final class ProxyingParent(childProps: Props, parentRelay: ActorRef) extends Actor {
  val child = context.actorOf(childProps, "child")

  val log = Logging(this)

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy()(PartialFunction.empty)

  override def receive: Receive = {
    case message =>
      log.debug("Forwarding {}", message)
      val target = if (sender() == child) parentRelay else child
      target.forward(message)
  }

  override def postStop(): Unit =
    log.debug("Stopped")
}
