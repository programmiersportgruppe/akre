package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress

import akka.actor._
import akka.event.Logging
import akka.io.{IO, Tcp}
import akka.io.Tcp.ConnectionClosed

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.protocol._


object RedisConnectionActor {
  case class NotReadyException(command: Command) extends RuntimeException("Received command before connected: " + command)
  case class UnableToConnectException(connectMessage: Tcp.Connect) extends RuntimeException("Unable to connect to Redis server with " + connectMessage)
}

abstract class RedisConnectionActor(serverAddress: InetSocketAddress, connectionSetupCommands: Seq[Command], messageToParentOnConnected: Option[Any]) extends Actor {
  import org.programmiersportgruppe.redis.client.RedisConnectionActor._

  val log = Logging(context.system, this)

  val replyReconstructor: ReplyReconstructor = new ParserCombinatorReplyReconstructor()

  val remote =
    if (serverAddress.isUnresolved) new InetSocketAddress(serverAddress.getHostName, serverAddress.getPort)
    else serverAddress
  log.debug("Connecting to Redis server at {}", remote)
  IO(Tcp)(context.system) ! Tcp.Connect(remote)

  protected def onReplyParsed(reply: RValue): Unit

  protected def onConnectionClosed(closed: ConnectionClosed): Unit = {}

  protected def onExecuteCommand(command: Command, listener: ActorRef): Unit = {}


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
        onExecuteCommand(command, listener)
        connection ! Tcp.Write(CommandSerializer.serialize(command))
      }

      for (command <- connectionSetupCommands)
        executeCommand(command, Actor.noSender)
      messageToParentOnConnected.map(context.parent ! _)
      log.info("Connected to Redis server at {} from local endpoint {} and ready to accept commands", remote, local)
      context become {
        case command: Command =>
          log.debug("Received command {}", command)
          executeCommand(command, sender())
        case Tcp.CommandFailed(w: Tcp.Write) =>
        // O/S buffer was full
        //                    listener ! "write failed"
        case Tcp.Received(data) =>
          if (log.isDebugEnabled)
            log.debug("Received {} bytes of data: [{}]", data.length, data.utf8String)

          replyReconstructor.process(data) { reply: RValue =>
            log.debug("Decoded reply {}", reply)
            onReplyParsed(reply)
          }
        case "close" =>
          connection ! Tcp.Close
        case closed: Tcp.ConnectionClosed if sender() == connection =>
          onConnectionClosed(closed)
      }
  }

}
