package org.programmiersportgruppe.redis

import akka.util.ByteString


trait ReplyReconstructor {
  def process(data: ByteString)(handleReply: Reply => _)
}
