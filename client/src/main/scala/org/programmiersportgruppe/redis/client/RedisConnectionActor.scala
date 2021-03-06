package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress

import akka.actor._
import akka.event.Logging
import akka.io.{IO, Tcp}
import akka.io.Tcp.ConnectionClosed

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.client.RedisConnectionActor._
import org.programmiersportgruppe.redis.protocol._


object RedisConnectionActor {

  case class NotReadyException(command: Command)
    extends RuntimeException("Received command before connected: " + command)

  case class UnableToConnectException(connectMessage: Tcp.Connect)
    extends RuntimeException("Unable to connect to Redis server with " + connectMessage)

}

/** A TCP connection to a Redis server
  *
  * When the actor is instantiated, it establishes a connection to a Redis server.
  * Once it has established the connection, it sends the `connectionSetupCommands` to the server,
  * and the optional `messageToParentOnConnected` to its parent.
  * If the actor fails to connect to the server, an [[RedisConnectionActor.UnableToConnectException]] is thrown.
  *
  * It will then send any commands it receives to the server,
  * and call `onReplyParsed` for each reply parsed from the server.
  * If a command is received before the connection to the server has been established,
  * a [[RedisConnectionActor.NotReadyException]] is thrown.
  *
  * @param serverAddress address to connect to
  * @param connectionSetupCommands commands to send immediately after establishing the connection
  * @param messageToParentOnConnected message to send to parent actor immediately after sending connection setup commands
  */
abstract class RedisConnectionActor(
    serverAddress: InetSocketAddress,
    connectionSetupCommands: Seq[Command],
    messageToParentOnConnected: Option[Any])
  extends Actor {

  protected[this] val log = Logging(context.system, this)

  private[this] val replyReconstructor: ReplyReconstructor = new ParserCombinatorReplyReconstructor()

  override def preStart(): Unit = {
    val resolvedServerAddress =
      if (serverAddress.isUnresolved) {
        log.debug(s"Resolving Redis server address for {}", serverAddress)
        new InetSocketAddress(serverAddress.getHostName, serverAddress.getPort)
      } else serverAddress

    log.debug("Connecting to Redis server at {}", resolvedServerAddress)
    IO(Tcp)(context.system) ! Tcp.Connect(resolvedServerAddress)
  }

  /** Callback for each reply parsed from the TCP stream from the server
    */
  protected def onReplyParsed(reply: RValue): Unit

  /** Callback invoked when the TCP connection to the server is closed
    *
    * @param closed connection closed message from the TCP socket
    */
  protected def onConnectionClosed(closed: ConnectionClosed): Unit = {
    log.info("Connection to Redis server closed: {}", closed)
    context.stop(self)
  }

  /** Callback invoked when a command is sent to the server
    *
    * @param command command that was sent to the server
    * @param listener reference to the actor that sent this command
    */
  protected def onExecuteCommand(command: Command, listener: ActorRef): Unit = {}


  final def receive = {

    case c @ Tcp.Connected(remote, local) =>
      log.info("Connected to Redis server at {} from local endpoint {}", remote, local)

      messageToParentOnConnected.foreach(context.parent ! _)

      val connection = context.watch(sender())
      connection ! Tcp.Register(self)

      def executeCommand(command: Command, listener: ActorRef): Unit = {
        connection ! Tcp.Write(CommandSerializer.serialize(command))
        onExecuteCommand(command, listener)
      }

      for (command <- connectionSetupCommands)
        executeCommand(command, Actor.noSender)

      context become {

        case command: Command =>
          log.debug("Received command {}", command)
          executeCommand(command, sender())

        case Tcp.Received(data) if sender() == connection =>
          if (log.isDebugEnabled)
            log.debug(s"Received ${data.length} bytes of data: [${data.utf8String}]")

          replyReconstructor.process(data) { reply: RValue =>
            log.debug("Decoded reply {}", reply)
            onReplyParsed(reply)
          }

        case closed: Tcp.ConnectionClosed if sender() == connection =>
          onConnectionClosed(closed)
      }

    case Tcp.CommandFailed(connect: Tcp.Connect) =>
      val e = new UnableToConnectException(connect)
      log.error(e.getMessage)
      throw e

    case command: Command =>
      val e = new NotReadyException(command)
      log.error(e.getMessage)
      throw e

  }

}
