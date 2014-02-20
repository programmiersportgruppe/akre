package org.programmiersportgruppe.redis

import scala.concurrent.duration._

import akka.actor._
import akka.actor.SupervisorStrategy.Stop
import akka.event.Logging
import akka.routing._


case object Ready

case class EmptyPoolException(handlingMessage: Any) extends Exception("Unable to handle message due to empty pool: " + handlingMessage)


object ResilientPool {
  def props(childProps: Props,
            size: Int,
            creationCircuitBreakerLogic: CircuitBreakerLogic = new CircuitBreakerLogic(2, OpenPeriodStrategy.doubling(1.second, 1.minute), 5.seconds),
            routingLogic: RoutingLogic = RoundRobinRoutingLogic()): Props =
    Props(classOf[ResilientPool], childProps, size, creationCircuitBreakerLogic, routingLogic)
}

class ResilientPool(childProps: Props,
                    size: Int,
                    creationCircuitBreakerLogic: CircuitBreakerLogic,
                    routingLogic: RoutingLogic) extends Actor {

  import context.dispatcher

  val log = Logging(context.system, this)
  val pendingWorkers = collection.mutable.Queue[(ActorRef, Deadline)]()
  var router: Option[Router] = None

  val creationCircuitBreaker = new EventDrivenCircuitBreaker(creationCircuitBreakerLogic) {

    var scheduledRecruitment: Option[Cancellable] = None

    override def onStateChanged(newState: CircuitBreakerState) {
      scheduledRecruitment.map(_.cancel())
      log.debug("Creation circuit breaker has changed to state " + state)
      scheduledRecruitment = newState match {
        case ho: creationCircuitBreakerLogic.HalfOpen => recruitAfter(ho.deadline)
        case o: creationCircuitBreakerLogic.Open => recruitAfter(o.deadline)
        case c: creationCircuitBreakerLogic.Closed => None
      }
    }

    def recruitAfter(deadline: Deadline) = {
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

  case object RecruitWorkers

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
    log.debug("Received {} from {}", msg, sender())
    super.aroundReceive(receive, msg)
  }

  def fireTardyWorkers() {
    while (pendingWorkers.headOption.exists(_._2.isOverdue())) {
      val (worker, _) = pendingWorkers.dequeue()
      log.warning("Stopping worker {}, which took too long to report ready.")
      context.unwatch(worker)
      context.stop(worker)
    }
  }

  def recruitWorkers() {
    fireTardyWorkers()

    for (_ <- 0 until (size - (activeWorkerCount + pendingWorkers.size)))
      if (creationCircuitBreaker.requestPermission()) {
        val worker = context.actorOf(childProps)
        context.watch(worker)
        pendingWorkers.enqueue(worker -> creationCircuitBreakerLogic.halfOpenTimeout.duration.fromNow)
        log.info("New worker {} pending activation", worker)
      }
  }
  
  def activeWorkerCount: Int = router.fold(0)(_.routees.size)

  def deactivateWorker(worker: ActorRef): Boolean = {
    val priorStatus =
      if (pendingWorkers.dequeueFirst(_._1 == worker).nonEmpty) {
        creationCircuitBreaker.reportFailure()
        Some("pending")
      } else if (router.exists(_.routees.exists(_ == ActorRefRoutee(worker)))) {
        router = router.flatMap(_.removeRoutee(worker) match {
          case r if r.routees.nonEmpty => Some(r)
          case _ => None
        })
        Some("active")
      } else {
        None
      }
    priorStatus.map { status =>
      log.warning("Removed {} worker {} (now {} of {})", status, worker, activeWorkerCount, size)
      recruitWorkers()
    }
    priorStatus.isDefined
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: Exception =>
      log.warning("Worker {} failed with {}: {}", sender(), e.getClass.getSimpleName, e.getMessage)
      deactivateWorker(sender())
      Stop
  }

  def receive = {
    case RecruitWorkers => recruitWorkers()
    case Ready if pendingWorkers.dequeueFirst(_._1 == sender()).isDefined =>
      creationCircuitBreaker.reportSuccess()
      router = Some(router.getOrElse(Router(routingLogic)).addRoutee(sender()))
      log.info(s"Worker {} activated (now {} of {})", sender(), activeWorkerCount, size)
    case Terminated(worker) => deactivateWorker(worker)
    case GetRoutees => sender ! Routees(router.fold(scala.collection.immutable.IndexedSeq.empty[Routee])(_.routees))
    case message if router.exists(_.routees.exists(_ == ActorRefRoutee(sender()))) => log.error("Unexpected message from active worker {}: {}", sender(), message)
    case message if pendingWorkers.exists(_._1 == sender()) => log.error("Unexpected message from pending worker {}: {}", sender(), message)
    case message => router match {
      case _ if sender() == ActorRef.noSender => log.error("WTF? incoming senderless message: {}", message)
      case Some(r) =>
        log.debug("Routing message {} for sender {}", message, sender())
        r.route(message, sender())
      case None =>
        log.warning("Can't deliver message {} for sender {} due to lack of workers", message, sender())
        sender ! akka.actor.Status.Failure(new EmptyPoolException(message))
        recruitWorkers()
    }
  }

}
