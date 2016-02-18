package org.programmiersportgruppe.redis.client

/** Indicates that a message sent to a pool could not be processed
  * because the pool had no active children when it was received.
  *
  * @param undeliverableMessage the message that couldn't be delivered
  */
case class EmptyPoolException(undeliverableMessage: Any)
  extends RuntimeException("The pool is currently empty and unable to handle this message: " + undeliverableMessage)
