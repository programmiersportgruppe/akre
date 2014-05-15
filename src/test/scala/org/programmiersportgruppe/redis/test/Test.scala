package org.programmiersportgruppe.redis.test

import org.scalatest.{ShouldMatchers, FlatSpec}
import org.scalautils.TypeCheckedTripleEquals
import akka.util.Timeout
import scala.concurrent.duration.FiniteDuration
import org.scalatest.concurrent.Eventually


class Test extends FlatSpec with TypeCheckedTripleEquals with ShouldMatchers with Eventually {

  def within[T](timeout: FiniteDuration)(x: => T): T = {
    val deadline = timeout.fromNow
    val result = x
    (timeout.fromNow - deadline) should be < timeout
    result
  }

}
