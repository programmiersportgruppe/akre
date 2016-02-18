package org.programmiersportgruppe.redis.client

/** A family of immutable circuit breaker states with transition methods that return the next state.
  *
  * This class is meant to abstract the logic of circuit-breaker state transitions away from the details of a circuit
  * breaker's API and state management, which might differ depending on the application. E.g., we might build an
  * event-driven circuit breaker with state managed by a simple variable for use in an Actor, or we might want a
  * thread-safe circuit breaker for use with a process that returns Futures.
  */
sealed trait CircuitBreakerState {

  /** Called by a circuit breaker to find out if an operation should be allowed to proceed.
    *
    * @return a pair of a boolean flag indicating if the operation should succeed, and the next circuit breaker state
    */
  def attemptOperation: (Boolean, CircuitBreakerState)

  /** Called by a circuit breaker when an operation succeeds
    *
    * @return the next circuit breaker state
    */
  def onSuccess: CircuitBreakerState

  /** Called by a circuit breaker when an operation fails
    *
    * @return the next circuit breaker state
    */
  def onFailure: CircuitBreakerState
}

object CircuitBreakerState {

  /** The circuit breaker is closed, meaning operations may proceed.
    *
    * @param settings            the circuit breaker's settings
    * @param consecutiveFailures the number of failures we have seen since we last saw a successful completion
    */
  final class Closed private(settings: CircuitBreakerSettings, consecutiveFailures: Int) extends CircuitBreakerState {
    def this(settings: CircuitBreakerSettings) = this(settings, 0)

    override def attemptOperation = true -> this

    override def onFailure =
      if (consecutiveFailures == settings.consecutiveFailureTolerance) new Open(settings, settings.openDurationProgression)
      else new Closed(settings, consecutiveFailures + 1)

    override def onSuccess =
      if (consecutiveFailures == 0) this
      else new Closed(settings)
  }

  /** The circuit breaker is open, meaning that operations may not proceed
    *
    * @param settings         the circuit breaker's settings
    * @param currentDurations the progression of durations for which to remain open before allowing a transition to
    *                         half-open
    */
  class Open private[CircuitBreakerState](settings: CircuitBreakerSettings, currentDurations: DurationProgression) extends CircuitBreakerState {
    val deadline = currentDurations.head.fromNow

    override def attemptOperation =
      if (deadline.isOverdue()) true -> new HalfOpen(settings, currentDurations.tail)
      else false -> this

    override def onFailure = this

    override def onSuccess = this
  }

  /** The circuit is half-open, meaning that it was open, but that a single test operation has been allowed to proceed
    * and we are waiting to find out whether it will be successful or not
    *
    * @param settings          the circuit breaker's settings
    * @param nextOpenDurations the progression of open durations to resume the open state with if we get a failure or timeout
    */
  class HalfOpen private[CircuitBreakerState](settings: CircuitBreakerSettings, nextOpenDurations: DurationProgression) extends CircuitBreakerState {
    val deadline = settings.halfOpenTimeout.fromNow

    override def attemptOperation =
      false -> {
        if (deadline.isOverdue()) new Open(settings, nextOpenDurations)
        else this
      }

    override def onFailure = new Open(settings, nextOpenDurations)

    override def onSuccess =
      if (deadline.isOverdue()) new Open(settings, nextOpenDurations)
      else new Closed(settings)
  }

}


