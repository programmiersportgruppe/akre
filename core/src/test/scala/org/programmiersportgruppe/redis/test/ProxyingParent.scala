package org.programmiersportgruppe.redis.test

import akka.actor.{ Actor, ActorRef, ActorRefFactory, Props, Terminated }
import akka.event.Logging

object ProxyingParent {

  def apply(childProps: Props, targetForMessagesFromChild: ActorRef, nameRoot: String)(implicit actorRefFactory: ActorRefFactory): ActorRef =
    actorRefFactory.actorOf(props(childProps, targetForMessagesFromChild), s"$nameRoot-parent")

  def props(childProps: Props, targetForMessagesFromChild: ActorRef): Props =
    Props(classOf[ProxyingParent], childProps, targetForMessagesFromChild)

}

final class ProxyingParent(childProps: Props, targetForMessagesFromChild: ActorRef) extends Actor {
  import context._

  val child = watch(actorOf(childProps, "child"))
  val log = Logging(this)

  override def receive: Receive = {

    case Terminated(`child`) =>
      log.debug("Stopping due to termination of child")
      stop(self)

    case message =>
      log.debug("Forwarding {}", message)
      val target =
        if (sender() == child) targetForMessagesFromChild
        else child
      target.forward(message)

  }
}
