package org.programmiersportgruppe.redis.protocol


class PushParserReplyReconstructorTest extends ReplyReconstructorTest {
  override def newReconstructor = new PushParserReplyReconstructor
}
