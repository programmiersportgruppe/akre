package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import scala.collection.mutable

import akka.actor._
import akka.io.{Tcp, IO}
import akka.event.Logging


object RedisConnectionActor {
  case class NotReadyException(command: Command) extends RuntimeException("Received command before connected: " + command)
  case class UnableToConnectException(connectMessage: Tcp.Connect) extends RuntimeException("Unable to connect as requested by " + connectMessage)
  case class UnexpectedlyClosedException(closedEvent: Tcp.ConnectionClosed, pendingCommands: Seq[(ActorRef, Command)]) extends RuntimeException(s"Connection unexpectedly closed with ${pendingCommands.length} command(s) pending: $closedEvent")

  def props(remote: InetSocketAddress, messageToParentOnConnected: Option[Any] = None): Props =
    Props(classOf[RedisConnectionActor], remote, messageToParentOnConnected)
}

class RedisConnectionActor(remote: InetSocketAddress, messageToParentOnConnected: Option[Any]) extends Actor with Stash {
  import RedisConnectionActor._

  val log = Logging(context.system, this)

  val pendingCommands = mutable.Queue[(ActorRef, Command)]()
  var replyReconstructor: ReplyReconstructor = new ParserCombinatorReplyReconstructor()

  log.debug("Connecting to {}", remote)
  IO(Tcp)(context.system) ! Tcp.Connect(remote)

  def receive = {

    case Tcp.CommandFailed(connect: Tcp.Connect) =>
      // TODO: send Failure messages to waiting actors
      throw new UnableToConnectException(connect)

    case command: Command =>
      stash()

    case c @ Tcp.Connected(`remote`, local) =>
      val connection = sender()
      context.watch(connection)
      connection ! Tcp.Register(self)
      messageToParentOnConnected.map(context.parent ! _)
      log.info("Connected to {} and ready to accept commands", remote)
      unstashAll()
      context become {
        case command: Command =>
          log.debug("Received command {}", command)
          pendingCommands.enqueue(sender() -> command)
          connection ! Tcp.Write(command.serialised)
        case Tcp.CommandFailed(w: Tcp.Write) =>
        // O/S buffer was full
        //                    listener ! "write failed"
        case Tcp.Received(data) =>
          log.debug("Received {} bytes of data: [{}]", data.length, data.utf8String)
          replyReconstructor.process(data) { reply: Reply =>
            log.debug("Decoded reply {}", reply)
            if (pendingCommands.isEmpty)
              throw new RuntimeException("Unexpected reply: " + reply)
            val (originalSender, command) = pendingCommands.dequeue()
            originalSender ! (command, reply)
          }
        case "close" =>
          connection ! Tcp.Close
        case closed: Tcp.ConnectionClosed if sender() == connection =>
          if (closed.isErrorClosed || pendingCommands.exists(!_.isInstanceOf[ConnectionCloseExpected])) {
            log.error("Connection unexpectedly closed with {} command(s) pending: {}", pendingCommands.length, closed)
            val e = new UnexpectedlyClosedException(closed, pendingCommands)
            pendingCommands.foreach {
              case (originalSender, _) => originalSender ! akka.actor.Status.Failure(e)
            }
            throw e
          } else pendingCommands.headOption match {
            case Some((originalSender, c: ConnectionCloseExpected)) =>
              log.info("Connection closed as expected: {}", closed)
              originalSender ! (())
              context stop self
            case _ =>
              log.info("Connection closed unexpectedly: {}", closed)
              context stop self
          }
      }
  }

}
