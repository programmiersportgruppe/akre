package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import scala.collection.mutable

import akka.actor.{Stash, ActorRef, Actor, Props}
import akka.io.{Tcp, IO}
import akka.event.Logging

object RedisConnectionActor {
  case class UnableToConnectException(connectMessage: Tcp.Connect) extends RuntimeException("Unable to connect as requested by " + connectMessage)
  case class NotReadyException(command: Command) extends RuntimeException("Received command before connected: " + command)

  def props(remote: InetSocketAddress) = Props(classOf[RedisConnectionActor], remote)
}

class RedisConnectionActor(remote: InetSocketAddress) extends Actor with Stash {
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
      context stop self

    case command: Command =>
      stash()

    case c @ Tcp.Connected(remote, local) =>
      //            listener ! c
      val connection = sender
      connection ! Tcp.Register(self)
      log.info("Connected to {} and ready to accept commands", remote)
      unstashAll()
      context become {
        case command: Command =>
          log.info("Received command {}", command)
          pendingCommands.enqueue(sender -> command)
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
        case _: Tcp.ConnectionClosed =>
          //                    listener ! "connection closed"
          context stop self
      }
  }

}
