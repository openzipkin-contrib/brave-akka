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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem.Settings
import akka.actor.setup.Setup
import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.dispatch.{Dispatcher, DispatcherPrerequisites, MessageDispatcherConfigurator}
import brave.Tracing
import com.typesafe.config.Config
import scala.concurrent.duration.DurationLong

object ZipkinExtensionId extends ExtensionId[ZipkinExtension] with ExtensionIdProvider {
  override def lookup = ZipkinExtensionId

  override def createExtension(system: ExtendedActorSystem): ZipkinExtension = {
    val setup = system.settings.setup.get(classOf[ZipkinSetup])

    val tracer = if (setup.isPresent) {
      setup.get().creator.apply(system)
    } else {
      NoopTracer
    }

    new ZipkinExtension(tracer)
  }

  def locateTracing(settings: Settings): Tracing = {
    settings.setup.get(classOf[ZipkinSetup]).get().tracing
  }
}

trait WithTracing {
  def tracing: Tracing
}

final class ZipkinSetup(val tracing:Tracing, val creator:ExtendedActorSystem => AkkaTracer) extends Setup with WithTracing

class ZipkinExtension(val tracer:AkkaTracer) extends Extension

class DispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites, tracing:Tracing) extends MessageDispatcherConfigurator(config, prerequisites) {
  override val dispatcher = new Dispatcher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    config.getDuration("throughput-deadline-time", TimeUnit.NANOSECONDS).nanos,
    configureExecutor(),
    config.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS).millis
  ) { dispatcher =>
    override def execute(runnable: Runnable) = {
      super.execute(tracing.currentTraceContext().wrap(runnable))
    }
  }
}

class DefaultDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
  extends DispatcherConfigurator(config, prerequisites, ZipkinExtensionId.locateTracing(prerequisites.settings))

