package tracing

import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch._
import com.typesafe.config.Config

class TracedMailbox extends MailboxType with ProducesMessageQueue[MessageQueue] {

  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = {
    val delegate = SingleConsumerOnlyUnboundedMailbox().create(owner, system)
    val tracer = system.map(ZipkinExtensionId(_).tracer)
    tracer.map(new TracedMessageQueue(_, delegate)).getOrElse(delegate)
  }

  private class TracedMessageQueue(tracer:AkkaTracer, delegate:MessageQueue) extends MessageQueue {
    override def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
      val newEnvelope = tracer.onEnqueue(handle, receiver)
      delegate.enqueue(receiver, newEnvelope)
    }

    override def dequeue(): Envelope = {
      var message = delegate.dequeue()
      if (message != null) {
        message = tracer.onDequeue(message)
      }
      message
    }

    override def numberOfMessages: Int = delegate.numberOfMessages

    override def hasMessages: Boolean = delegate.hasMessages

    override def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = delegate.cleanUp(owner, deadLetters)
  }
}
