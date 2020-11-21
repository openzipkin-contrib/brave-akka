/**
 * Copyright 2018-2020 The OpenZipkin Authors
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

import akka.actor.{ActorContext, ActorRef}
import akka.dispatch.Envelope
import brave.{Span, Tracing}


case class TracedMessage(message:Any, span:Span)

trait AkkaTracer  {

  def onTell(actor:ActorRef, message:Any): Any = message

  def onAsk(actor:ActorRef, message:Any): Any = message

  def onEnqueue(envelope:Envelope, actor: ActorRef): Envelope = envelope

  def onDequeue(envelope:Envelope): Envelope = envelope

  def aroundReceive(aroundReceive: Any => Unit, context:ActorContext, message:Any): Unit = aroundReceive.apply(message)

}


class ZipkinTracer(tracing:Tracing) extends AkkaTracer {
  private val tracer = tracing.tracer()

  override def onTell(actor: ActorRef, message: Any): Any = {
    TracedMessage(message, tracer.nextSpan())
  }

  override def aroundReceive(aroundReceive: Any => Unit, context: ActorContext, message:Any): Unit = {
    message match {
      case traced:TracedMessage =>
        val span = traced.span
        span.name(context.self.path.name)
        span.tag("akka.system.name", context.system.name)
        span.start()
        val scope = tracing.currentTraceContext().newScope(span.context())
        try
          aroundReceive.apply(traced.message)
        catch {case e: Exception => span.tag("error", e.getMessage); throw e}
        finally  {
          scope.close()
          span.finish()
        }
      case _ => aroundReceive.apply(message)
    }
  }
}


// dummy tracer
object NoopTracer extends AkkaTracer
