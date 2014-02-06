package org.programmiersportgruppe.redis

import org.scalatest.FunSuite
import akka.util.ByteString

class ByteStringReaderTest extends FunSuite {

  test("Can extract prefix of a given length") {
    val byteString = ByteString("This is a string")
    val reader = new ByteStringReader(byteString, 0)

    val (extracted, remainder) = reader.extract(10)

    assertResult(Some(ByteString("This is a ")))(extracted)
    assertResult(new ByteStringReader(byteString, 10))(remainder)
  }

  test("Gives none when trying to extract more bytes than available") {
    val byteString = ByteString("This is a string")
    val reader = new ByteStringReader(byteString, 0)

    val (extracted, remainder) = reader.extract(20)

    assertResult(None)(extracted)
    assertResult(reader)(remainder)
  }

}
