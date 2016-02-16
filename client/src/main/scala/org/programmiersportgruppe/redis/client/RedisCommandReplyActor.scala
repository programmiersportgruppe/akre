package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress

import akka.actor.{Props, Actor, ActorRef}
import akka.io.Tcp
import akka.io.Tcp.ConnectionClosed
import org.programmiersportgruppe.redis.client.RedisCommandReplyActor.UnexpectedlyClosedException
import org.programmiersportgruppe.redis.commands.ConnectionCloseExpected
import org.programmiersportgruppe.redis.{Command, RValue}

import scala.collection.mutable

object RedisCommandReplyActor {

  case class UnexpectedlyClosedException(closedEvent: Tcp.ConnectionClosed, pendingCommands: Seq[(ActorRef, Command)])
    extends RuntimeException(s"Connection to Redis server unexpectedly closed with ${pendingCommands.length} command(s) pending: $closedEvent")

  def props(serverAddress: InetSocketAddress, connectionSetupCommands: Seq[Command] = Nil, messageToParentOnConnected: Option[Any] = None): Props =
    Props(classOf[RedisCommandReplyActor], serverAddress, connectionSetupCommands, messageToParentOnConnected)

}

class RedisCommandReplyActor(serverAddress: InetSocketAddress, connectionSetupCommands: Seq[Command], messageToParentOnConnected: Option[Any])
  extends RedisConnectionActor(serverAddress, connectionSetupCommands, messageToParentOnConnected) {

  val pendingCommands = mutable.Queue[(ActorRef, Command)]()

  override protected def onReplyParsed(reply: RValue): Unit = {
    assert(pendingCommands.nonEmpty, s"Received a completely unexpected reply: $reply")
    pendingCommands.dequeue() match {
      case (Actor.noSender, _) => // nothing to do
      case (originalSender, command) => originalSender ! (command -> reply)
    }
  }

  override protected def onExecuteCommand(command: Command, listener: ActorRef): Unit =
    pendingCommands.enqueue(listener -> command)

  override protected def onConnectionClosed(closed: ConnectionClosed): Unit =
    if (closed.isErrorClosed || pendingCommands.exists(!_._2.isInstanceOf[ConnectionCloseExpected])) {
      val e = new UnexpectedlyClosedException(closed, pendingCommands)
      log.error(e.getMessage)
      pendingCommands.foreach {
        case (originalSender, _) => originalSender ! akka.actor.Status.Failure(e)
      }
      throw e
    } else pendingCommands.headOption match {
      case Some((originalSender, c: ConnectionCloseExpected)) =>  // TODO: what about other commands fire with executeConnectionClose?
        log.info("Connection to Redis server closed as expected: {}", closed)
        originalSender ! (())
        context stop self
      case _ =>
        log.warning("Connection to Redis server closed unexpectedly: {}", closed)
        context stop self
    }

}
