package org.programmiersportgruppe.redis.client

import java.net.InetSocketAddress

import akka.actor.Props
import akka.util.ByteString

import org.programmiersportgruppe.redis.{RInteger, RBulkString, RArray, RValue}
import org.programmiersportgruppe.redis.client.RedisSubscriptionActor.PubSubMessage
import org.programmiersportgruppe.redis.commands.SUBSCRIBE

object RedisSubscriptionActor {

  def props(serverAddress: InetSocketAddress, messageToParentOnSubscribed: Option[Any], channels: Seq[ByteString], onMessage: PubSubMessage => Unit): Props =
    Props(classOf[RedisSubscriptionActor], serverAddress, messageToParentOnSubscribed, channels, onMessage)

  case class PubSubMessage(channel: ByteString, content: ByteString) {
    override def toString: String = s"${channel.utf8String} <${content.length} bytes>"
  }

  private val MessageEventSigil = ByteString("message")
  private val SubscribeEventSigil = ByteString("subscribe")
  private val UnsubscribeEventSigil = ByteString("unsubscribe")

}

class RedisSubscriptionActor(serverAddress: InetSocketAddress, messageToParentOnSubscribed: Option[Any], channels: Seq[ByteString], onMessage: PubSubMessage => Unit)
  extends RedisConnectionActor(serverAddress, channels match { case head +: tail => Seq(SUBSCRIBE(head, tail: _*)) }, None) {

  import RedisSubscriptionActor._

  val subscriptions = scala.collection.mutable.Queue[ByteString]()

  override protected def onReplyParsed(reply: RValue): Unit =
    reply match {

      case RArray(Seq(RBulkString(Some(event)), RBulkString(Some(channel)), payload)) =>
        event match {

          case SubscribeEventSigil =>
            assert(channel == channels(subscriptions.length))
            subscriptions.enqueue(channel)
            assert(payload == RInteger(subscriptions.length.toLong))
            if (subscriptions.length == channels.length)
              messageToParentOnSubscribed.foreach(context.parent ! _)

          case MessageEventSigil =>
            val RBulkString(Some(content)) = payload
            onMessage(PubSubMessage(channel, content))

          case UnsubscribeEventSigil =>
            throw new UnsupportedOperationException("Wasn't expecting unsubscribe event")

          case _ =>
            throw new UnsupportedOperationException(s"Received unexpected event: $event")
        }

      case _ =>
        throw new RuntimeException(s"Received reply with unexpected structure: $reply")
    }
}
