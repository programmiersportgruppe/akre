package org.programmiersportgruppe.redis

import akka.util.ByteString
import org.programmiersportgruppe.redis.test.Test

class ByteStringReaderTest extends Test {

  behavior of "a byte string reader"

  it can "extract prefix of a given length" in {
    val byteString = ByteString("This is a string")
    val reader = new ByteStringReader(byteString, 0)

    val (extracted, remainder) = reader.extract(10)

    assertResult(Some(ByteString("This is a ")))(extracted)
    assertResult(new ByteStringReader(byteString, 10))(remainder)
  }

  it should "give None when trying to extract more bytes than available" in {
    val byteString = ByteString("This is a string")
    val reader = new ByteStringReader(byteString, 0)

    val (extracted, remainder) = reader.extract(20)

    assertResult(None)(extracted)
    assertResult(reader)(remainder)
  }

}
