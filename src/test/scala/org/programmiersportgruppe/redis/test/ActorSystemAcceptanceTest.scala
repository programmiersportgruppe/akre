package org.programmiersportgruppe.redis.test

import java.net.InetSocketAddress
import scala.concurrent.{Promise, Future, Await, Awaitable}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.sys.process.ProcessLogger


class ActorSystemAcceptanceTest extends Test {

  implicit val executionContext = scala.concurrent.ExecutionContext.global
  implicit val timeout: Timeout = 5.seconds

  def await(awaitable: Awaitable[_]) = Await.result(awaitable, 5 seconds)

  def withRedisServer(testCode: InetSocketAddress => Any) {
    val address = new InetSocketAddress("localhost", 4321)

    val output = new StringBuilder
    val serverReady = Promise[Unit]()

    val server = sys.process.Process(Seq("/usr/local/bin/redis-server"
      , "--port", address.getPort.toString
      , "--bind", address.getHostName
      , "--save", ""  // disable saving state to disk
    )).run(ProcessLogger { line =>
      note("Redis server output: " + line.replaceAll("\r?\n?$", ""))
      output.append(line)
      if (line contains "The server is now ready to accept connections")
        serverReady.success()
    })
    try {
      serverReady.tryCompleteWith(Future {
        val exitStatus = server.exitValue()
        if (!serverReady.isCompleted)
          throw new RuntimeException("Server terminated unexpectedly with exit status " + exitStatus + ". Output follows:\n" + output)
      })
      await(serverReady.future)
      testCode(address)
    } finally {
      server.destroy()
      server.exitValue()
    }
  }

  def withActorSystem(testCode: ActorSystem => Any) {
    val actorSystem = ActorSystem("Test-actor-system-for-" + getClass.getSimpleName, ConfigFactory.parseString(
      """
      akka {
        loglevel = DEBUG
        log-dead-letters = 100
        actor.debug.unhandled = on
      }
      deque-dispatcher {
         type = Dispatcher
      }
      """
    ))
    try {
      testCode(actorSystem)
    } finally {
      actorSystem.shutdown()
    }
  }

}
