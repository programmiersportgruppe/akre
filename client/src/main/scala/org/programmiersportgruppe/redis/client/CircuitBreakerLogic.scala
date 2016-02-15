package org.programmiersportgruppe.redis.client

import scala.concurrent.duration.FiniteDuration

import akka.util.Timeout


object DurationProgression {

  def doubling(first: FiniteDuration, max: FiniteDuration): DurationProgression =
    exponential(2, first, max)

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

class DurationProgression(val head: FiniteDuration, computeNext: FiniteDuration => FiniteDuration) {
  def tail: DurationProgression = new DurationProgression(computeNext(head), computeNext)
}

abstract class CircuitBreakerState {
  def attemptOperation: (CircuitBreakerState, Boolean)
  def onSuccess: CircuitBreakerState
  def onFailure: CircuitBreakerState
}

class CircuitBreakerLogic(consecutiveFailureTolerance: Int, openDurations: DurationProgression, val halfOpenTimeout: Timeout) {

  class Closed(consecutiveFailures: Int) extends CircuitBreakerState {
    override def attemptOperation = this -> true

    override def onFailure =
      if (consecutiveFailures == consecutiveFailureTolerance) new Open(openDurations)
      else new Closed(consecutiveFailures + 1)

    override def onSuccess =
      if (consecutiveFailures == 0) this
      else new Closed(0)
  }

  class Open(durations: DurationProgression) extends CircuitBreakerState {
    val deadline = durations.head.fromNow

    override def attemptOperation =
      if (deadline.isOverdue()) new HalfOpen(durations.tail) -> true
      else this -> false

    override def onFailure = this

    override def onSuccess = this
  }

  class HalfOpen(openDurations: DurationProgression) extends CircuitBreakerState {
    val deadline = halfOpenTimeout.duration.fromNow

    override def attemptOperation = (
      if (deadline.isOverdue()) new Open(openDurations)
      else this
    ) -> false

    override def onFailure = new Open(openDurations)

    override def onSuccess =
      if (deadline.isOverdue()) new Open(openDurations)
      else new Closed(0)
  }

}

class EventDrivenCircuitBreaker(logic: CircuitBreakerLogic) {
  def this(consecutiveFailureTolerance: Int, openDurations: DurationProgression, halfOpenTimeout: FiniteDuration) =
    this(new CircuitBreakerLogic(consecutiveFailureTolerance, openDurations, halfOpenTimeout))

  var state: CircuitBreakerState = new logic.Closed(0)

  def transitionTo(newState: CircuitBreakerState): Unit = {
    if (state != newState) {
      state = newState
      onStateChanged(state)
    }
  }

  def reportFailure(): Unit = {
    transitionTo(state.onFailure)
  }

  def reportSuccess(): Unit = {
    transitionTo(state.onSuccess)
  }

  def requestPermission(): Boolean = {
    val (newState, operationAllowed) = state.attemptOperation
    transitionTo(newState)
    operationAllowed
  }

  def onStateChanged(newState: CircuitBreakerState): Unit = {}

}
