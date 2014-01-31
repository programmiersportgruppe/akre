package org.programmiersportgruppe.redis

import akka.util.ByteString


sealed abstract class Reply
case class ErrorReply(error: String) extends Reply

sealed abstract class ProperReply extends Reply
case class StatusReply(status: String) extends ProperReply
case class IntegerReply(value: Long) extends ProperReply
case class BulkReply(data: Option[ByteString]) extends ProperReply
case class MultiBulkReply(replies: Seq[Reply]) extends ProperReply
