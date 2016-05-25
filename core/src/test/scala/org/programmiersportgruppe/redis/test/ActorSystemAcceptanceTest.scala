package org.programmiersportgruppe.redis.test

import java.net.{ InetAddress, InetSocketAddress }
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._
import scala.concurrent._
import scala.sys.process.ProcessLogger

import akka.pattern.ask
import akka.actor.{ ActorIdentity, ActorSystem, Identify }
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.exceptions.TestFailedException


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
    withRedisServer()(testCode)

  def withRedisServer[A](address: InetSocketAddress = new InetSocketAddress(LoopbackAddress.get, redisServerPort))(testCode: InetSocketAddress => A): A = {

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

    val stopServer = new Thread {
      override def run(): Unit = {
        server.destroy()
        server.exitValue()
      }
    }

    try
      try {
        Runtime.getRuntime.addShutdownHook(stopServer)
        serverReady.tryCompleteWith(Future {
          val exitStatus = server.exitValue()
          if (!serverReady.isCompleted)
            throw new RuntimeException("Server terminated unexpectedly with exit status " + exitStatus)
        })
        await(serverReady.future)
        testCode(address)
      } finally {
        stopServer.run()
        Runtime.getRuntime.removeShutdownHook(stopServer)
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
    try {
      val result = testCode(actorSystem)
      await(actorSystem.actorSelection("/user/*") ? Identify(())) match {
        case ActorIdentity(_, Some(actorRef)) => throw new TestFailedException(s"There is at least one user actor still running: $actorRef", 1)
        case _                                => result
      }
    } finally TestKit.shutdownActorSystem(actorSystem, verifySystemShutdown = true)
  }

}
