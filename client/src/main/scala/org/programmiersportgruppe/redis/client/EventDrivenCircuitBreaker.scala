package org.programmiersportgruppe.redis.client

class EventDrivenCircuitBreaker(options: CircuitBreakerOptions) {

  private var state: CircuitBreakerState = new CircuitBreakerState.Closed(options)

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
