package org.programmiersportgruppe.redis.client

import scala.concurrent.duration.FiniteDuration

import akka.util.Timeout


object OpenPeriodStrategy {
  def doubling(first: FiniteDuration, max: FiniteDuration): Stream[FiniteDuration] = {
    require(first < max)
    val double = first * 2
    first #:: (if (double < max) doubling(double, max) else Stream.continually(max))
  }
}

abstract class CircuitBreakerState {
  def attemptOperation: (CircuitBreakerState, Boolean)
  def onSuccess: CircuitBreakerState
  def onFailure: CircuitBreakerState
}

class CircuitBreakerLogic(consecutiveFailureTolerance: Int, openPeriods: Stream[FiniteDuration], val halfOpenTimeout: Timeout) {

  class Closed(consecutiveFailures: Int) extends CircuitBreakerState {
    override def attemptOperation = this -> true

    override def onFailure =
      if (consecutiveFailures == consecutiveFailureTolerance) new Open(openPeriods)
      else new Closed(consecutiveFailures + 1)

    override def onSuccess =
      if (consecutiveFailures == 0) this
      else new Closed(0)
  }

  class Open(periods: Stream[FiniteDuration]) extends CircuitBreakerState {
    val deadline = periods.head.fromNow

    override def attemptOperation =
      if (deadline.isOverdue()) new HalfOpen(periods.tail) -> true
      else this -> false

    override def onFailure = this

    override def onSuccess = this
  }

  class HalfOpen(nextOpenPeriods: Stream[FiniteDuration]) extends CircuitBreakerState {
    val deadline = halfOpenTimeout.duration.fromNow

    override def attemptOperation = (
      if (deadline.isOverdue()) new Open(nextOpenPeriods)
      else this
    ) -> false

    override def onFailure = new Open(nextOpenPeriods)

    override def onSuccess =
      if (deadline.isOverdue()) new Open(nextOpenPeriods)
      else new Closed(0)
  }

}

class EventDrivenCircuitBreaker(logic: CircuitBreakerLogic) {
  def this(consecutiveFailureTolerance: Int, openPeriods: Stream[FiniteDuration], halfOpenTimeout: FiniteDuration) =
    this(new CircuitBreakerLogic(consecutiveFailureTolerance, openPeriods, halfOpenTimeout))

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
