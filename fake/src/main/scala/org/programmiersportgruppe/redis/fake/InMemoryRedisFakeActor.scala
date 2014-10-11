package org.programmiersportgruppe.redis.fake

import akka.actor.{Actor, Props}

import org.programmiersportgruppe.redis._
import org.programmiersportgruppe.redis.commands._


object InMemoryRedisFakeActor {
    def props(): Props = Props(classOf[InMemoryRedisFakeActor])
}

class InMemoryRedisFakeActor extends Actor {
    var value: RBulkString = null

    override def receive = {
        case command: Command => sender ! command -> (command match {
            case c@SET(_, _, _, _) =>
                this.value = RBulkString(c.value)
                RSimpleString.OK
            case c@GET(_) =>
                value
        })
    }
}
