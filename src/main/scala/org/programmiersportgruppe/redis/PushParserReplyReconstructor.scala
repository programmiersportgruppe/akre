package org.programmiersportgruppe.redis

import scala.collection.mutable
import akka.util.ByteString

class PushParserReplyReconstructor {
  private val extracted = mutable.Queue[Reply]()
//  private var state: State = ExpectingReply()(None)

  abstract class State {
    def onData(data: ByteString)
  }

//  case class ExpectingReply(implicit cont: Option[Reply => Unit]) extends State {
//    override def onData(data: ByteString) {
//      state = data.head match {
//        case '+' => ReadingUntilNewline(StatusReply)
//        case '-' => ReadingUntilNewline(ErrorReply)
//        case ':' => ReadingInteger(IntegerReply)
//        case '$' => ReadingInteger {
//          case -1 => cont(BulkReply(None))
//          case  n => state = ReadingBytes(n)
//        }
//        case '*' => ReadingInteger { n =>
//          state = ReadingMultipleReplies(n)
//        }
//      }
//      continue(data.tail)
//    }
//  }
//  case class ReadingUntilNewline(replyBuilder: ByteString => Reply, buffer: ByteString)(implicit cont: Reply => Unit) extends State {
//    override def onData(data: ByteString) {
//      if (buffer.nonEmpty && buffer.last == '\n' && data.head == '\r') {
//        continue(data.tail, replyBuilder(buffer.dropRight(1)))
//      } else {
//        var index = data.indexOf('\r')
//        while (index != -1) {
//          val next = index + 1
//          if (next < data.length && data(next) == '\n') {
//            return continue(data.drop(next + 1), replyBuilder(buffer + data.take(index)))
//          }
//          index = data.indexOf('\r', next)
//        }
//        state = copy(buffer += data)
//      }
//    }
//  }
//  case class ReadingInteger(replyBuilder: Int => Reply, value: Int)(implicit cont: Reply => Unit) extends State {
//    override def onData(data: ByteString) {
//      data.head match {
//        case digit if digit >= '0' && digit <= '9' =>
//          state = copy(value = value * 10 + (digit - '0'))
//          continue(data.tail)
//        case '\n' =>
//          assert(data.tail.head == '\r')
//          continue(data.tail.tail, replyBuilder(value))
//      }
//    }
//  }
//  case class ReadingBytes(count: Int, buffer: ByteString) {
//    override def onData(data: ByteString) {
//      val all = buffer + data
//      if (all.length > count) {
//        assert(all(n) == '\r')
//        if (all.length > count + 1) {
//          assert(all(count + 1) == '\n')
//          continue(all.drop(count + 2), BulkReply(Some(all.take(count))))
//        }
//      }
//    }
//  }
//  case class ReadingMultipleReplies(count: Int, replies: Seq[Reply]) {
//    // not a state per se?
//  }

//  def addData(data: ByteString) {
//    buffer ++= data // buffer shared, outside of state?
//    if (data.nonEmpty)
//      state.onData(data)
//  }
//
//  def extractNext: Option[Reply] = {
//    reply(new ByteStringReader(buffer)) match {
//      case Success(reply, remainder) =>
//        buffer = remainder.asInstanceOf[ByteStringReader].byteString
//        Some(reply)
//      case Failure("end of input", _) => None
//      case f: Failure => throw new RuntimeException("Unexpected parse failure: " + f)
//      case e: Error => throw new RuntimeException("Parse error: " + e)
//    }
//  }

//  def extractStream: Stream[Reply] = extractNext match {
//    case Some(reply) => reply #:: extractStream
//    case None => Stream.empty
//  }
}
