/**
 * Copyright 2016-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
