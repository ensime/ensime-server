// Copyright: 2010 - 2017 https://github.com/ensime/ensime-server/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.server

import akka.actor._
import akka.event.LoggingReceive
import org.ensime.api._
import org.ensime.core._
import org.ensime.io.Canon.ops._
import scalaz.ioeffect.RTS

/**
 * Accepts RpcRequestEnvelope and responds with an RpcResponseEnvelope to target.
 * Also sends asynchronous RpcResponseEnvelopes to target.
 * Ensures that everything in and out is canonised.
 */
class ConnectionHandler(
  project: ActorRef,
  broadcaster: ActorRef,
  target: ActorRef
) extends Actor
    with ActorLogging
    with RTS {

  override def preStart(): Unit =
    broadcaster ! Broadcaster.Register

  override def postStop(): Unit =
    broadcaster ! Broadcaster.Unregister

  // not Receive, thanks to https://issues.scala-lang.org/browse/SI-8861
  // (fixed in 2.11.7)
  def receive: PartialFunction[Any, Unit] =
    receiveRpc orElse LoggingReceive { receiveEvents }

  def receiveRpc: Receive = {
    case req: RpcRequestEnvelope =>
      val handler = RequestHandler(unsafePerformIO(req.canon), project, self)
      context.actorOf(handler, s"${req.callId}")

    case outgoing: RpcResponseEnvelope =>
      target forward unsafePerformIO(outgoing.canon)
  }

  def receiveEvents: Receive = {
    case outgoing: EnsimeEvent =>
      target forward RpcResponseEnvelope(None, unsafePerformIO(outgoing.canon))
  }

}
object ConnectionHandler {
  def apply(
    project: ActorRef,
    broadcaster: ActorRef,
    target: ActorRef
  ): Props = Props(classOf[ConnectionHandler], project, broadcaster, target)
}
