package org.programmiersportgruppe.redis

import org.scalautils.TypeCheckedTripleEquals
import org.scalatest.FlatSpec
import akka.util.ByteString
import scala.collection.mutable

class ParserCombinatorReplyReconstructorTest extends FlatSpec with TypeCheckedTripleEquals {

  behavior of "a reply reconstructor"

  it should "reconstruct a status reply" in {
    val reconstructor = new ParserCombinatorReplyReconstructor
    val replies = mutable.Queue[Reply]()

    reconstructor.process(ByteString("+Some status\r\n")) { r: Reply =>
      replies += r
    }

    assertResult(Seq(StatusReply("Some status"))) {
      replies
    }
  }

}
