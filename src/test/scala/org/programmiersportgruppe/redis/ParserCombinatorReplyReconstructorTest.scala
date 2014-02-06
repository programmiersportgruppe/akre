package org.programmiersportgruppe.redis

import akka.util.ByteString
import scala.collection.mutable
import org.programmiersportgruppe.redis.test.Test

class ParserCombinatorReplyReconstructorTest extends Test {

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

  it should "store data until it has a full reply when end of input during scanning" in {
    val reconstructor = new ParserCombinatorReplyReconstructor
    val replies = mutable.Queue[Reply]()

    reconstructor.process(ByteString("+Some ")) { r: Reply =>
      replies += r
    }
    assertResult(Nil) { replies }

    reconstructor.process(ByteString("status\r\n")) { r: Reply =>
      replies += r
    }

    assertResult(Seq(StatusReply("Some status"))) {
      replies
    }
  }

  it should "store data until it has a full reply when end of input during consumption of fixed number of bytes" in {
    val reconstructor = new ParserCombinatorReplyReconstructor
    val replies = mutable.Queue[Reply]()

    reconstructor.process(ByteString("$11\r\nSome ")) { r: Reply =>
      replies += r
    }
    assertResult(Nil) { replies }

    reconstructor.process(ByteString("status\r\n")) { r: Reply =>
      replies += r
    }

    assertResult(Seq(BulkReply(Some(ByteString("Some status"))))) {
      replies
    }
  }

  it should "handle multiple replies arriving at once" in {
    val reconstructor = new ParserCombinatorReplyReconstructor
    val replies = mutable.Queue[Reply]()

    reconstructor.process(ByteString("+Status A\r\n+Status B\r\n")) { r: Reply =>
      replies += r
    }

    assertResult(Seq(
      StatusReply("Status A"),
      StatusReply("Status B"))
    ) {
      replies
    }
  }

}
