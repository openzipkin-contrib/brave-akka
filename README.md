An experiment for adding *transparent* zipkin tracing capabilities to akka.
Transparent in this context means no modifications to existing Actor/ActorRef/Message implementations. Also it avoids using java agent instrumentation.
"Business code" (actors/messages) should remain as is (without tracing API dependency) 
Only [extensions](https://doc.akka.io/docs/akka/2.5/extending-akka.html) and custom `ActorSystem`(s) are allowed. 

See [Zipkin Issue 450](https://github.com/openzipkin/brave/issues/450) for discussion details.

This is work in progress and API is unstable.

