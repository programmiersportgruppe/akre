package org.programmiersportgruppe.redis.protocol

import scala.collection.mutable

import akka.util.ByteString

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.test.Test


abstract class ReplyReconstructorTest extends Test {
  def newReconstructor: ReplyReconstructor

  behavior of "a reply reconstructor"

  it should "reconstruct a simple string (formerly 'status reply')" in {
    val reconstructor = newReconstructor
    val replies = mutable.Queue[RValue]()

    reconstructor.process(ByteString("+Some status\r\n")) { r: RValue =>
      replies += r
    }

    assertResult(Seq(RSimpleString("Some status"))) {
      replies
    }
  }

  it should "store data until it has a full reply when end of input during scanning" in {
    val reconstructor = newReconstructor
    val replies = mutable.Queue[RValue]()

    reconstructor.process(ByteString("+Some ")) { r: RValue =>
      replies += r
    }
    assertResult(Nil) { replies }

    reconstructor.process(ByteString("status\r\n")) { r: RValue =>
      replies += r
    }

    assertResult(Seq(RSimpleString("Some status"))) {
      replies
    }
  }

  it should "store data until it has a full reply when end of input during consumption of fixed number of bytes" in {
    val reconstructor = newReconstructor
    val replies = mutable.Queue[RValue]()

    reconstructor.process(ByteString("$11\r\nSome ")) { r: RValue =>
      replies += r
    }
    assertResult(Nil) { replies }

    reconstructor.process(ByteString("status\r\n")) { r: RValue =>
      replies += r
    }

    assertResult(Seq(RBulkString("Some status"))) {
      replies
    }
  }

  it should "handle multiple replies arriving at once" in {
    val reconstructor = newReconstructor
    val replies = mutable.Queue[RValue]()

    reconstructor.process(ByteString("+Status A\r\n+Status B\r\n")) { r: RValue =>
      replies += r
    }

    assertResult(Seq(
      RSimpleString("Status A"),
      RSimpleString("Status B"))
    ) {
      replies
    }
  }

}
