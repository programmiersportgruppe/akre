package org.programmiersportgruppe.redis.test

import java.net.{InetAddress, InetSocketAddress}
import scala.concurrent.{Promise, Future, Await, Awaitable}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.sys.process.ProcessLogger
import java.util.concurrent.atomic.AtomicInteger
import org.programmiersportgruppe.redis.ResilientPoolMailbox

object ActorSystemAcceptanceTest {
  val nextRedisServerPort = new AtomicInteger(4321)
}

class ActorSystemAcceptanceTest extends Test {

  implicit val executionContext = scala.concurrent.ExecutionContext.global
  implicit val timeout: Timeout = 5.seconds

  def await(awaitable: Awaitable[_]) = Await.result(awaitable, 5.seconds)

  lazy val redisServerPort = ActorSystemAcceptanceTest.nextRedisServerPort.getAndIncrement

  def withRedisServer[A](testCode: InetSocketAddress => A): A = {
    val address = new InetSocketAddress(InetAddress.getLoopbackAddress, redisServerPort)

    val output = new StringBuilder
    val serverReady = Promise[Unit]()

    val server = sys.process.Process(Seq("/usr/local/bin/redis-server"
      , "--port", address.getPort.toString
      , "--bind", address.getAddress.getHostAddress
      , "--save", ""  // disable saving state to disk
    )).run(ProcessLogger { line =>
      while(
        try {
          info("Redis server output: " + line.replaceAll("\r?\n?$", ""))
          false
        } catch {
          case e: InterruptedException => true
        }
      ) ()
      output.append(line)
      if (line contains "The server is now ready to accept connections")
        serverReady.success(())
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

  def withActorSystem[A](testCode: ActorSystem => A): A = {
    val actorSystem = ActorSystem("Test-actor-system-for-" + getClass.getSimpleName, ConfigFactory.parseString(
      s"""
      akka {
        loglevel = DEBUG
        log-dead-letters = 100
        actor.debug.unhandled = on

        actor.deployment {
          /akre-redis-pool {
            dispatcher = akre-coordinator-dispatcher
          }
          "/akre-redis-pool/*" {
            dispatcher = akre-connection-dispatcher
          }
        }
      }
      akre-coordinator-dispatcher {
        type = PinnedDispatcher
        mailbox-type = "${classOf[ResilientPoolMailbox].getName}"
      }
      akre-connection-dispatcher {
        type = PinnedDispatcher
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
