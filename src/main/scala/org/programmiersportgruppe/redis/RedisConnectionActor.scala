package org.programmiersportgruppe.redis

import java.net.InetSocketAddress
import scala.collection.mutable

import akka.actor._
import akka.io.{Tcp, IO}
import akka.event.Logging


object RedisConnectionActor {
  case class NotReadyException(command: Command) extends RuntimeException("Received command before connected: " + command)
  case class UnableToConnectException(connectMessage: Tcp.Connect) extends RuntimeException("Unable to connect to Redis server with " + connectMessage)
  case class UnexpectedlyClosedException(closedEvent: Tcp.ConnectionClosed, pendingCommands: Seq[(ActorRef, Command)]) extends RuntimeException(s"Connection to Redis server unexpectedly closed with ${pendingCommands.length} command(s) pending: $closedEvent")

  def props(hostName: String, hostPort: Int, connectionSetupCommands: Seq[Command] = Nil, messageToParentOnConnected: Option[Any] = None): Props =
    Props(classOf[RedisConnectionActor], hostName, hostPort, connectionSetupCommands, messageToParentOnConnected)
}

class RedisConnectionActor(hostName: String, hostPort: Int, connectionSetupCommands: Seq[Command], messageToParentOnConnected: Option[Any]) extends Actor with Stash {
  import RedisConnectionActor._

  val log = Logging(context.system, this)

  val pendingCommands = mutable.Queue[(ActorRef, Command)]()
  var replyReconstructor: ReplyReconstructor = new ParserCombinatorReplyReconstructor()

  val address = new InetSocketAddress(hostName, hostPort)

  log.debug("Connecting to Redis server host {} at IP {} port {}", hostName, address.getAddress.getHostAddress, hostPort)
  IO(Tcp)(context.system) ! Tcp.Connect(address)

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

    case c @ Tcp.Connected(`address`, local) =>
      val connection = sender()
      context.watch(connection)
      connection ! Tcp.Register(self)

      def executeCommand(command: Command, listener: ActorRef): Unit = {
        pendingCommands.enqueue(listener -> command)
        connection ! Tcp.Write(command.serialised)
      }

      for (command <- connectionSetupCommands)
        executeCommand(command, Actor.noSender)
      messageToParentOnConnected.map(context.parent ! _)
      log.info("Connected to Redis server at {} from local endpoint {} and ready to accept commands", address, local)
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
            case Some((originalSender, c: ConnectionCloseExpected)) =>
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
