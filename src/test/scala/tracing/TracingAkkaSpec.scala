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

import java.util.concurrent.{ArrayBlockingQueue, CountDownLatch, TimeUnit}

import akka.actor.setup.ActorSystemSetup
import akka.actor.{Actor, ActorSystem, BootstrapSetup, ExtendedActorSystem, Props, TracedActorSystem}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActors, TestKit}
import brave.Tracing
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}
import zipkin2.Span
import zipkin2.reporter.Reporter

import scala.concurrent.duration._

object Ack

class TracingAkkaSpec
  extends TestKit(TracingAkkaSpec.system)
    with DefaultTimeout with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfter with BeforeAndAfterAll  {


  private val spans =  TracingAkkaSpec.reporter.spans

  before {
    TracingAkkaSpec.reporter.reset()
  }

  override def afterAll {
    shutdown()
  }

  "Basic tracing" should {
    "Simple single message" in {
      within(500 millis) {
        val ref = system.actorOf(TestActors.echoActorProps)
        ref ! Ack
        expectMsg(Ack)
        val span = spans.poll(100, TimeUnit.MILLISECONDS)
        assert(span != null)
        span.tags() should not be empty
      }
    }

    "Errors in actor" in {
      within(500 millis) {
        val ref = system.actorOf(Props(classOf[FailingActor]), classOf[FailingActor].getSimpleName)
        ref ! Ack
        assert(receiveOne(10 millis) == null) // shouldn't receive any message because of error
        val span = spans.poll(100, TimeUnit.MILLISECONDS)
        assert(span != null)
        span.tags() should not be empty
        span.tags().keySet() should contain ("error")
        span.tags().get("error") should equal("boom")
      }
    }

    "Parent / Child span" in {
      within(500 millis) {
        val parent = TracingAkkaSpec.tracing.tracer().nextSpan()
        parent.name("parent")
        val scope = TracingAkkaSpec.tracing.tracer().withSpanInScope(parent)
        val ref = system.actorOf(TestActors.echoActorProps)
        ref ! "test"
        expectMsg("test")
        scope.close()
        parent.finish()
        val received = Array(spans.poll(100, TimeUnit.MILLISECONDS), spans.poll(100, TimeUnit.MILLISECONDS)).filter(_ != null)
        received should have length 2
        val s1 = received.find(_.name() == "parent").get
        val s2 = received.find(_.id() != s1.id()).get

        assert(s2.parentId() == s1.id())
        assert(s1.parentId() == null)
      }
    }

    "Different thread" in {
      within(500 millis) {
        val latch = new CountDownLatch(1)
        val runnable = new Runnable() { override def run() {system.actorOf(Props(classOf[FailingActor])) ! "test"; latch.countDown()}}
        new Thread(runnable, "Thread-local-1111").start() // send from a different Thread
        latch.await()
        receiveOne(100 millis)
        val span = spans.poll(100, TimeUnit.MILLISECONDS)
        assert(span != null)
        span.tags() should not be empty
      }
    }
  }

}

class FailingActor extends Actor {
  override def receive: Receive = {
    case _ => throw new RuntimeException("boom")
  }
}

object TracingAkkaSpec {
  val config = s"""
    akka {
      loglevel = "WARNING"
    }

    traced-mailbox {
          mailbox-type = "${classOf[TracedMailbox].getName}"
    }
    """


  val reporter = new TestReporter()
  val tracing = Tracing.newBuilder().spanReporter(reporter).build()

  private val local = ActorSystem("TestKitUsageSpec", ActorSystemSetup(BootstrapSetup(ConfigFactory.parseString(config)), new ZipkinSetup(tracing, _ => new ZipkinTracer(tracing))))
  local.registerExtension(ZipkinExtensionId)
  val system = new TracedActorSystem(local.asInstanceOf[ExtendedActorSystem])
}

class TestReporter extends Reporter[Span] {
  val spans = new ArrayBlockingQueue[Span](8)

  override def report(span: Span): Unit = spans.put(span)

  def reset():Unit = {
    spans.clear()
  }
}
