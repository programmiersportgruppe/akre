package org.programmiersportgruppe.redis.client

import java.util.concurrent.TimeoutException

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.dispatch._
import akka.event.Logging
import akka.pattern.ask
import akka.routing._
import com.typesafe.config.Config
import org.programmiersportgruppe.redis.ImmediateExecutionOnCallingThread
import org.programmiersportgruppe.redis.client.ResilientPoolActor._

import scala.util.{Failure, Success}

object ResilientPoolActor {

  def props(
      size: Int,
      childProps: Props,
      creationCircuitBreakerSettings: CircuitBreakerSettings,
      routingLogic: RoutingLogic = RoundRobinRoutingLogic()
  ): Props =
    Props(classOf[ResilientPoolActor], size, childProps, creationCircuitBreakerSettings, routingLogic)

  def completeOnActiveChildren(scheduler: Scheduler, pool: ActorRef, timeout: FiniteDuration, minConnections: Int = 1, pollingInterval: FiniteDuration = 10.millis, queryTolerance: FiniteDuration = 10.millis): Future[Unit] = {
    val complete = Promise[Unit]()
    val deadline = timeout.fromNow

    def checkCurrentChildCount(): Unit = {
      val queryTimeout = (deadline.timeLeft max Duration.Zero) + queryTolerance
      pool.ask(GetRoutees)(queryTimeout).onComplete {
        case Success(routees) =>
          if (routees.asInstanceOf[Routees].routees.size >= minConnections)
            complete.success(())
          else {
            val timeLeft = deadline.timeLeft
            if (timeLeft < Duration.Zero)
              complete.failure(new TimeoutException(s"Exceeded $timeout timeout while waiting for at least $minConnections connections"))
            else scheduler.scheduleOnce(pollingInterval min timeLeft) {
              checkCurrentChildCount()
            }(ImmediateExecutionOnCallingThread)
          }
        case Failure(e) =>
          complete.failure(e)
      }(ImmediateExecutionOnCallingThread)
    }
    checkCurrentChildCount()

    complete.future
  }

  /** The message should be sent by the child actors to their parent (the pool)
    * when they are ready to have messages routed to them.
    */
  case object ChildReady

  case object RecruitWorkers


  object Mailbox {

    class MessageQueue extends UnboundedPriorityMailbox.MessageQueue(11, PriorityGenerator {
      // internal
      case ChildReady     => 1
      case Terminated(_)  => 2
      case RecruitWorkers => 3

      // external
      case GetRoutees => 10
      case _          => 20
    })

  }

  class Mailbox(settings: ActorSystem.Settings, config: Config)
    extends MailboxType with ProducesMessageQueue[Mailbox.MessageQueue] {

    override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
      new Mailbox.MessageQueue()
  }

}


/** An actor maintaining a resilient, fixed-size pool of children.
  *
  * Messages sent to the pool are routed to one of the active children.
  *
  * When children are created, they will not receive any messages until they have sent a
  * [[ResilientPoolActor.ChildReady]] message to the pool. If a child dies, it is recreated. Child creation is
  * controlled by a circuit breaker to prevent endless loops when conditions cause children to fail immediately.
  *
  * The pool responds to [[akka.routing.GetRoutees$]] with the collection of active children.
  *
  * @param size                           the number of children to maintain in the pool
  * @param childProps                     properties for creating the children
  * @param routingLogic                   the logic to use in routing messages to the children
  * @param creationCircuitBreakerSettings settings for the child creation circuit breaker
  */
final class ResilientPoolActor(
    size: Int,
    childProps: Props,
    creationCircuitBreakerSettings: CircuitBreakerSettings,
    routingLogic: RoutingLogic
) extends Actor with RequiresMessageQueue[ResilientPoolActor.Mailbox.MessageQueue] {

  import context.dispatcher

  private[this] val log = Logging(context.system, this)
  private[this] val pendingWorkers = collection.mutable.Queue[(ActorRef, Deadline)]()
  private[this] var router: Router = Router(routingLogic)

  private[this] val creationCircuitBreaker = new EventDrivenCircuitBreaker(creationCircuitBreakerSettings) {

    var scheduledRecruitment: Option[Cancellable] = None

    override def onStateChanged(newState: CircuitBreakerState): Unit = {
      scheduledRecruitment.foreach(_.cancel())
      log.debug("Creation circuit breaker has changed to state " + newState)

      import CircuitBreakerState._
      scheduledRecruitment = recruitAfter(newState match {
        case ho: HalfOpen => ho.deadline
        case o: Open      => o.deadline
        case c: Closed    => Deadline.now
      })
    }

    private def recruitAfter(deadline: Deadline): Option[Cancellable] = {
      val timeLeft = deadline.timeLeft
      if (timeLeft > Duration.Zero) {
        log.debug("Scheduling recruitment after state change in " + timeLeft)
        Some(context.system.scheduler.scheduleOnce(timeLeft, self, RecruitWorkers))
      } else {
        log.debug("Immediately recruiting as state change can already take place)")
        recruitWorkers()
        None
      }
    }
  }

  recruitWorkers()

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
    log.debug("Received {} from {}", msg, sender())
    super.aroundReceive(receive, msg)
  }

  private def fireTardyWorkers(): Unit = {
    while (pendingWorkers.headOption.exists(_._2.isOverdue())) {
      val (worker, _) = pendingWorkers.dequeue()
      log.warning("Stopping worker {}, which took too long to report ready.", worker)
      context.unwatch(worker)
      context.stop(worker)
    }
  }

  private def recruitWorkers(): Unit = {
    fireTardyWorkers()

    for (_ <- 0 until (size - (activeWorkerCount + pendingWorkers.size)))
      if (creationCircuitBreaker.requestPermission()) {
        val worker = context.actorOf(childProps)
        context.watch(worker)
        pendingWorkers.enqueue(worker -> creationCircuitBreakerSettings.halfOpenTimeout.fromNow)
        log.info("New worker {} pending activation", worker)
      }
  }

  private def activeWorkerCount: Int = router.routees.size

  private def deactivateWorker(worker: ActorRef): Unit = {
    val priorStatus =
      if (pendingWorkers.dequeueFirst(_._1 == worker).nonEmpty) {
        creationCircuitBreaker.reportFailure()
        Some("pending")
      } else if (router.routees.contains(ActorRefRoutee(worker))) {
        router = router.removeRoutee(worker)
        Some("active")
      } else {
        None
      }
    priorStatus.foreach { status =>
      log.warning("Removed {} worker {} (now {} of {})", status, worker, activeWorkerCount, size)
      recruitWorkers()
    }
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: Exception =>
      log.warning("Worker {} failed with {}: {}", sender(), e.getClass.getSimpleName, e.getMessage)
      deactivateWorker(sender())
      Stop
  }

  override def receive = {
    case RecruitWorkers     => recruitWorkers()
    case ChildReady         =>
      if (pendingWorkers.dequeueFirst(_._1 == sender()).isDefined) {
        creationCircuitBreaker.reportSuccess()
        router = router.addRoutee(sender())
        log.info(s"Worker {} activated (now {} of {})", sender(), activeWorkerCount, size)
      } else {
        log.error(s"Unexpectedly received ChildReady message from ${sender()}. Actually waiting for: $pendingWorkers")
      }
    case GetRoutees         => sender ! Routees(router.routees)
    case Terminated(worker) => deactivateWorker(worker)
    case message            =>
      if (pendingWorkers.exists(_._1 == sender()))
        log.error("Unexpected message from pending worker {}: {}", sender(), message)
      if (router.routees.isEmpty) {
        log.warning("Can't deliver message {} for sender {} due to lack of workers", message, sender())
        sender ! akka.actor.Status.Failure(new EmptyPoolException(message))
        recruitWorkers()
      } else if (router.routees.contains(ActorRefRoutee(sender())))
        log.error("Unexpected message from active worker {}: {}", sender(), message)
      else {
        log.debug("Routing message {} for sender {}", message, sender())
        router.route(message, sender())
      }
  }

}
