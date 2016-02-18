package org.programmiersportgruppe.redis.client

import scala.concurrent.duration.FiniteDuration

/** An infinite progression of finite durations to be used in some process.
  *
  * This is a memory-friendly alternative to Stream[FiniteDuration].
  *
  * @param head             the first duration in the progression
  * @param computeSuccessor a lambda to compute the next duration in the progression
  */
final class DurationProgression(val head: FiniteDuration, computeSuccessor: FiniteDuration => FiniteDuration) {
  def tail: DurationProgression = new DurationProgression(computeSuccessor(head), computeSuccessor)
}

object DurationProgression {

  /** A progression that starts with `first`, then repeatedly doubles the value until it is clamped at `max`.
    */
  def doubling(first: FiniteDuration, max: FiniteDuration): DurationProgression =
    exponential(2, first, max)

  /** An exponential progression of base `base`, starting at `first` and increasing until clamped at `max`.
    */
  def exponential(base: Long, first: FiniteDuration, max: FiniteDuration): DurationProgression = {
    require(base > 0, s"base (got $base) should greater than 1 to produce a monotonically increasing sequence")
    require(first < max, s"the first duration (got $first) should be less than the max (got $max)")
    new DurationProgression(first, { previous =>
      previous * base match {
        case d if d < max => d
        case _            => max
      }
    })
  }

}
