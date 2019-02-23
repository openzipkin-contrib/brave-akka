name := "akka-tracing"

version := "1.0"

scalaVersion := "2.11.12"

val akkaVersion = "2.5.9"
val braveVersion = "4.12.0"
val zipkinReporterVersion = "2.2.3"
val scalaTestVersion = "3.0.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
)

libraryDependencies ++= Seq(
  "io.zipkin.brave" % "brave" % braveVersion,
  "io.zipkin.brave" % "brave-instrumentation-jaxrs2" % braveVersion,
  "io.zipkin.reporter2" % "zipkin-reporter" % zipkinReporterVersion,
  "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % zipkinReporterVersion
)

libraryDependencies += "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
