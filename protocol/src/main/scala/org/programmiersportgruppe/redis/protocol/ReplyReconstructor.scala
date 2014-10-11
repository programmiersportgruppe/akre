package org.programmiersportgruppe.redis.protocol

import akka.util.ByteString

import org.programmiersportgruppe.redis._


trait ReplyReconstructor {
  def process(data: ByteString)(handleReply: RValue => _): Unit
}
