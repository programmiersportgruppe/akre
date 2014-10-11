package org.programmiersportgruppe.redis

class RedisClientException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this(message: String) = this(message, null)
}


case class ErrorReplyException(command: Command, reply: RError)
  extends RedisClientException(s"Error reply received: ${reply}\nFor command: $command")

case class UnexpectedReplyException(command: Command, reply: RSuccessValue)
  extends RedisClientException(s"Unexpected reply received: ${reply}\nFor command: $command")

case class RequestExecutionException(command: Command, cause: Throwable)
  extends RedisClientException(s"Error while executing command [$command]: ${cause.getMessage}", cause)
