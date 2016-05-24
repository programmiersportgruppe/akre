package org.programmiersportgruppe.redis.test

import java.net.{ InetAddress, InetSocketAddress }
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ Await, Awaitable, Future, Promise }
import scala.concurrent.duration._
import scala.sys.process.ProcessLogger

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory


object ActorSystemAcceptanceTest {
  def addressIfReachable(address: String): Option[InetAddress] =
    Option(InetAddress.getByName(address)).filter(_.isReachable(5000))

  val IPv4LoopbackAddress = addressIfReachable("127.0.0.1")
  val IPv6LoopbackAddress = addressIfReachable("::1")
  val LoopbackAddress = IPv6LoopbackAddress orElse IPv4LoopbackAddress

  val nextRedisServerPort = new AtomicInteger(4321)
}

class ActorSystemAcceptanceTest extends Test {

  import ActorSystemAcceptanceTest._

  implicit val executionContext = scala.concurrent.ExecutionContext.global
  implicit val timeout: Timeout = 5.seconds

  def await(awaitable: Awaitable[_]) = Await.result(awaitable, 5.seconds)

  lazy val redisServerPort = nextRedisServerPort.getAndIncrement

  def withRedisServer[A](testCode: InetSocketAddress => A): A =
    withRedisServer(new InetSocketAddress(LoopbackAddress.get, redisServerPort))(testCode)

  def withRedisServer[A](address: InetSocketAddress)(testCode: InetSocketAddress => A): A = {

    val log = new StringBuffer("Redis server log:\n")
    val serverReady = Promise[Unit]()

    val server = sys.process.Process(Seq("redis-server"
      , "--port", address.getPort.toString
      , "--bind", Option(address.getAddress).fold(address.getHostName)(_.getHostAddress.replaceAll("%.*[a-zA-Z].*", ""))
      , "--save", "" // disable saving state to disk
    )).run(ProcessLogger { line =>
      if (line contains "The server is now ready to accept connections")
        serverReady.success(())
      log.append(line).append('\n')
    })
    try
      try {
        serverReady.tryCompleteWith(Future {
          val exitStatus = server.exitValue()
          if (!serverReady.isCompleted)
            throw new RuntimeException("Server terminated unexpectedly with exit status " + exitStatus)
        })
        await(serverReady.future)
        testCode(address)
      } finally {
        server.destroy()
        server.exitValue()
      }
    catch {
      case e: Throwable =>
        info(log.toString)
        throw e
    }
  }

  def withActorSystem[A](testCode: ActorSystem => A): A = {
    val actorSystem = ActorSystem("Test-actor-system-for-" + getClass.getSimpleName, ConfigFactory.parseString(
      s"""
      akka {
        loglevel = DEBUG
        log-dead-letters = 100
        actor.debug.unhandled = on
      }
      """
    ))
    try testCode(actorSystem)
    finally TestKit.shutdownActorSystem(actorSystem, verifySystemShutdown = true)
  }

}
