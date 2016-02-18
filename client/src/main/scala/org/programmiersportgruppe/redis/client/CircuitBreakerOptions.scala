package org.programmiersportgruppe.redis.client

import scala.concurrent.duration.FiniteDuration

/** Options to control a circuit breaker's behaviour
  *
  * @param consecutiveFailureTolerance number of consecutive errors that will be tolerated before opening the circuit
  * @param openDurationProgression     progression of durations for which the circuit should be kept open between
  *                                    half-open periods; when becoming open after having been closed, the first
  *                                    duration in the progression is used; when becoming open after having been
  *                                    half-open, the next duration in the progression is used
  * @param halfOpenTimeout             timeout after switching to half-open before the circuit will flip back to open if
  *                                    it hasn't received the result of the attempt
  */
case class CircuitBreakerOptions(
    consecutiveFailureTolerance: Int,
    openDurationProgression: DurationProgression,
    halfOpenTimeout: FiniteDuration)
