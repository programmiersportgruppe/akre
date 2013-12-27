package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed abstract class Reply

case class StatusReply(status: String) extends Reply
case class ErrorReply(error: String) extends Reply
case class IntegerReply(value: Int) extends Reply
case class BulkReply(data: Option[ByteString]) extends Reply
case class MultiBulkReply(replies: Seq[Reply]) extends Reply
