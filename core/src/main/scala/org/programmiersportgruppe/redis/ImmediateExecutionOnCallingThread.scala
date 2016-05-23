package org.programmiersportgruppe.redis

import scala.concurrent.ExecutionContext

/** An execution context that can be used to remove the scheduling overhead for simple `Future` transformations.
  *
  * WARNING: Inappropriate use of this `ExecutionContext` could result in crazy behaviour or deadlocks.
  *          Make sure you understand what you're doing with it.
  */
protected[redis] object ImmediateExecutionOnCallingThread extends ExecutionContext {
  override def execute(runnable: Runnable): Unit = runnable.run()
  override def reportFailure(t: Throwable): Unit = ()
}
