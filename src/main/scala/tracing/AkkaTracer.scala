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
