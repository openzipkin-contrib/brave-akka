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
package akka.actor

import java.util.concurrent.ThreadFactory

import akka.dispatch.sysmsg.SystemMessage
import akka.dispatch.{Dispatchers, Mailboxes}
import akka.event.{EventStream, LoggingAdapter, LoggingFilter}
import tracing.{AkkaTracer, ZipkinExtension, ZipkinExtensionId}

import scala.concurrent.{ExecutionContextExecutor, Future}


final class TracedActor(delegate:Actor, tracer: AkkaTracer) extends Actor {

  override protected[akka] def aroundReceive(receive: Receive, msg: Any): Unit =
    tracer.aroundReceive(msg => delegate.aroundReceive(receive, msg), delegate.context, msg)

  override def preStart(): Unit = delegate.preStart()

  override def postStop(): Unit = delegate.postStop()

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = delegate.preRestart(reason, message)

  override def postRestart(reason: Throwable): Unit = delegate.postRestart(reason)

  override def unhandled(message: Any): Unit = delegate.unhandled(message)

  override def receive: Receive = delegate.receive

}

class TracedActorRef(delegate:ActorRef, tracer:AkkaTracer) extends MinimalActorRef {
  override def path: ActorPath = delegate.path

  override def forward(message: Any)(implicit context: ActorContext): Unit = delegate.forward(message)

  override def !(message: Any)(implicit sender: ActorRef = Actor.noSender) = {
    val newMessage = tracer.onTell(delegate, message)
    delegate.tell(newMessage, sender)
  }

  override def start(): Unit = delegate.asInstanceOf[InternalActorRef].start()
  override def resume(causedByFailure: Throwable): Unit = delegate.asInstanceOf[InternalActorRef].resume(causedByFailure)
  override def suspend(): Unit = delegate.asInstanceOf[InternalActorRef].suspend()
  override def restart(cause: Throwable): Unit = delegate.asInstanceOf[InternalActorRef].restart(cause)
  override def stop(): Unit = delegate.asInstanceOf[InternalActorRef].stop()
  override def sendSystemMessage(message: SystemMessage): Unit = delegate.asInstanceOf[InternalActorRef].sendSystemMessage(message)

  override def provider: ActorRefProvider = delegate.asInstanceOf[InternalActorRef].provider

  override def getParent: InternalActorRef = delegate.asInstanceOf[InternalActorRef].getParent

  override def getChild(name: Iterator[String]): InternalActorRef = delegate.asInstanceOf[InternalActorRef].getChild(name)

  override private[akka] def isTerminated = delegate.isTerminated

}

class FakeActorProducer(props: Props, tracer: AkkaTracer) extends IndirectActorProducer {

  override def produce(): Actor = {
    val delegate = props.producer.produce()

    val old = ActorCell.contextStack.get()
    ActorCell.contextStack.set(List(delegate.context))
    val actor = new TracedActor(delegate, tracer)
    ActorCell.contextStack.set(old)
    actor
  }

  override def actorClass: Class[_ <: Actor] = {
    props.actorClass()
  }
}

class TracedActorSystem(delegate:ExtendedActorSystem) extends ExtendedActorSystem {

  if (!delegate.hasExtension(ZipkinExtensionId)) {
    throw new IllegalStateException(s"Extension ${classOf[ZipkinExtension]} is not registered for ${delegate.name}")
  }

  private val tracer = ZipkinExtensionId(delegate).tracer

  private[this] def overrideProps(props:Props): Props = Props(classOf[FakeActorProducer], props, tracer).withMailbox("traced-mailbox")


  override def actorOf(props: Props): ActorRef = new TracedActorRef(delegate.actorOf(overrideProps(props)), tracer)

  override def actorOf(props: Props, name: String): ActorRef = new TracedActorRef(delegate.actorOf(overrideProps(props), name), tracer)

  override def name: String = delegate.name

  override def settings: ActorSystem.Settings = delegate.settings

  override def logConfiguration(): Unit = delegate.logConfiguration

  override def /(name: String): ActorPath = delegate./(name)

  override def /(name: Iterable[String]): ActorPath = delegate./(name)

  override def eventStream: EventStream = delegate.eventStream

  override def log: LoggingAdapter = delegate.log

  override def deadLetters: ActorRef = delegate.deadLetters

  override def scheduler: Scheduler = delegate.scheduler

  override def dispatchers: Dispatchers = delegate.dispatchers

  override implicit def dispatcher: ExecutionContextExecutor = delegate.dispatcher

  override def mailboxes: Mailboxes = delegate.mailboxes

  override def registerOnTermination[T](code: => T): Unit = delegate.registerOnTermination(code)

  override def registerOnTermination(code: Runnable): Unit = delegate.registerOnTermination(code)

  override def terminate(): Future[Terminated] = delegate.terminate()

  override def whenTerminated: Future[Terminated] = delegate.whenTerminated

  override def registerExtension[T <: Extension](ext: ExtensionId[T]): T = delegate.registerExtension(ext)

  override def extension[T <: Extension](ext: ExtensionId[T]): T = delegate.extension(ext)

  override def hasExtension(ext: ExtensionId[_ <: Extension]): Boolean = delegate.hasExtension(ext)

  override protected def systemImpl: ActorSystemImpl = ???

  override def provider: ActorRefProvider = delegate.provider

  override def guardian: InternalActorRef = delegate.guardian

  override protected def lookupRoot: InternalActorRef = ???

  override def stop(actor: ActorRef): Unit = delegate.stop(actor)

  override def systemGuardian: InternalActorRef = delegate.systemGuardian

  override def systemActorOf(props: Props, name: String): ActorRef = delegate.systemActorOf(props, name)

  override def threadFactory: ThreadFactory = delegate.threadFactory

  override def dynamicAccess: DynamicAccess = delegate.dynamicAccess

  override def logFilter: LoggingFilter = delegate.logFilter

  override private[akka] def printTree = delegate.printTree
}
