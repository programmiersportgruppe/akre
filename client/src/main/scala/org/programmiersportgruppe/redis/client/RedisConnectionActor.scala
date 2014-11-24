package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress
import scala.collection.mutable

import akka.actor._
import akka.event.Logging
import akka.io.{IO, Tcp}

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands.ConnectionCloseExpected
import org.programmiersportgruppe.redis.protocol._


object RedisConnectionActor {
  case class NotReadyException(command: Command) extends RuntimeException("Received command before connected: " + command)
  case class UnableToConnectException(connectMessage: Tcp.Connect) extends RuntimeException("Unable to connect to Redis server with " + connectMessage)
  case class UnexpectedlyClosedException(closedEvent: Tcp.ConnectionClosed, pendingCommands: Seq[(ActorRef, Command)]) extends RuntimeException(s"Connection to Redis server unexpectedly closed with ${pendingCommands.length} command(s) pending: $closedEvent")

  def props(serverAddress: InetSocketAddress, connectionSetupCommands: Seq[Command] = Nil, messageToParentOnConnected: Option[Any] = None): Props =
    Props(classOf[RedisConnectionActor], serverAddress, connectionSetupCommands, messageToParentOnConnected)
}

class RedisConnectionActor(serverAddress: InetSocketAddress, connectionSetupCommands: Seq[Command], messageToParentOnConnected: Option[Any]) extends Actor with Stash {
  import org.programmiersportgruppe.redis.client.RedisConnectionActor._

  val log = Logging(context.system, this)

  val pendingCommands = mutable.Queue[(ActorRef, Command)]()
  var replyReconstructor: ReplyReconstructor = new ParserCombinatorReplyReconstructor()

  val remote =
    if (serverAddress.isUnresolved) new InetSocketAddress(serverAddress.getHostName, serverAddress.getPort)
    else serverAddress
  log.debug("Connecting to Redis server at {}", remote)
  IO(Tcp)(context.system) ! Tcp.Connect(remote)

  def receive = {

    case Tcp.CommandFailed(connect: Tcp.Connect) =>
      // TODO: send Failure messages to waiting actors
      val e = new UnableToConnectException(connect)
      log.error(e.getMessage)
      throw e

    case command: Command =>
      val e = new NotReadyException(command)
      log.error(e.getMessage)
      throw e

    case c @ Tcp.Connected(`remote`, local) =>
      val connection = sender()
      context.watch(connection)
      connection ! Tcp.Register(self)

      def executeCommand(command: Command, listener: ActorRef): Unit = {
        pendingCommands.enqueue(listener -> command)
        connection ! Tcp.Write(CommandSerializer.serialize(command))
      }

      for (command <- connectionSetupCommands)
        executeCommand(command, Actor.noSender)
      messageToParentOnConnected.map(context.parent ! _)
      log.info("Connected to Redis server at {} from local endpoint {} and ready to accept commands", remote, local)
      unstashAll()
      context become {
        case command: Command =>
          log.debug("Received command {}", command)
          executeCommand(command, sender())
        case Tcp.CommandFailed(w: Tcp.Write) =>
        // O/S buffer was full
        //                    listener ! "write failed"
        case Tcp.Received(data) =>
          log.debug("Received {} bytes of data: [{}]", data.length, data.utf8String)
          replyReconstructor.process(data) { reply: RValue =>
            log.debug("Decoded reply {}", reply)
            assert(pendingCommands.nonEmpty, "Received a completely unexpected reply")
            pendingCommands.dequeue() match {
              case (Actor.noSender, _)       => // nothing to do
              case (originalSender, command) => originalSender ! (command -> reply)
            }
          }
        case "close" =>
          connection ! Tcp.Close
        case closed: Tcp.ConnectionClosed if sender() == connection =>
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
  }

}
