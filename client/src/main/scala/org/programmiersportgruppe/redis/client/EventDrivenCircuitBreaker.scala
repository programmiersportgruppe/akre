package org.programmiersportgruppe.redis.client

/** An event-driven circuit breaker with non-thread-safe state management,
  * suitable for use from an actor.
  */
class EventDrivenCircuitBreaker(settings: CircuitBreakerSettings) {

  private var state: CircuitBreakerState = new CircuitBreakerState.Closed(settings)

  def transitionTo(newState: CircuitBreakerState): Unit =
    if (state != newState) {
      state = newState
      onStateChanged(state)
    }

  def reportFailure(): Unit =
    transitionTo(state.onFailure)

  def reportSuccess(): Unit =
    transitionTo(state.onSuccess)

  def requestPermission(): Boolean = {
    val (operationAllowed, newState) = state.attemptOperation
    transitionTo(newState)
    operationAllowed
  }

  def onStateChanged(newState: CircuitBreakerState): Unit = {}

}
