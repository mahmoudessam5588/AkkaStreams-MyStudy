package com.AkkaStreams.TechniquesAndPatterns

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.reflect.ClassTag.Int

//Actors Can Interact With Akka Streams In:
//A)process elements in streams
//B)act as a source
//C)act as destination
//D)or even inside the Akka Streams
object IntegrationWithActors extends App {
  given actorSystem: ActorSystem = ActorSystem("WithActors")
  given materialize: Materializer = Materializer(actorSystem)
  class TypicalActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case str: String =>
        log.info(s"Received a:$str")
        sender() ! s"$str & $str"
      case n: Int =>
        log.info(s"Received an Int NO:$n")
        sender() ! n * 2
      case _ =>
    }
  }
  val typicalActor = actorSystem.actorOf(Props[TypicalActor](), "IntActor")
  val sourceInt = Source(1 to 10)

  /*actor as a flow with special method ask*/
  //ask take two arguments
  //here the signature:
  //def ask[S](parallelism: Int)(ref: ActorRef)(implicit timeout: Timeout, tag: ClassTag[S])
  //parallelism: Int ==>how many messages can be in this actor's mailbox at any onetime before actors start backPressuring
  //ref: ActorRef ==> actor reference {typicalActor}
  //Implicit TimeOut ==> A Timeout is a wrapper on top of Duration to be more specific about what the duration means
  //tag = constraint for type of variable at runtime using reflection
  given timeout: Timeout = Timeout(2 second)
  //val actorBasedFlow = Flow[Int].ask(parallelism = 4)(typicalActor)(using timeout,tag = Int)
  //short hand of the above
  val actorBasedFlow = Flow[Int].ask[Int](4)(typicalActor)
  sourceInt.via(actorBasedFlow).to(Sink.ignore).run()
  //shorter hand
  sourceInt.ask[Int](4)(typicalActor).to(Sink.ignore).run()
  //-------------------------------------------------------------------------------------
  /*actors as a source
  * here we want actor ref exposed to you so yo can send messages to it
  * when you send messages to one of these actors ref means you're injecting
  * messages into the stream*/
  //val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead){{deprecated}}
  //new method signature
  /*def actorRef[T](
      completionMatcher: PartialFunction[Any, CompletionStrategy],
      failureMatcher: PartialFunction[Any, Throwable],
      bufferSize: Int,
      overflowStrategy: OverflowStrategy)*/
  val actorPoweredSource = Source.actorRef[Int](
    completionMatcher  = {
      case Done => CompletionStrategy.immediately
    },
    failureMatcher = PartialFunction.empty,
    bufferSize = 10,
    OverflowStrategy.dropHead
  )
  val materializeValueOfActorRef: ActorRef =
    actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor Powered Flow Got Number $number"))).run()
  materializeValueOfActorRef ! 10
  //terminating the stream
  materializeValueOfActorRef ! akka.actor.Status.Success("complete")
  //-----------------------------------------------------------------------------
  /*actor as a destination or a sink
  * an init message
  * an ack message
  * a complete message
  * function to generate message in case streams throws an exception */
  //Actor powered Sink will need to support an initialization message which will be sent first by whichever component
  //ends up connected to this actor powered sink && acknowledged message which is sent from this actor to the stream
  //to confirm the perception of an element && a complete message {support function message}to generate messages in-case
  //streams throws an exception
  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)
  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream Initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream Complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed $ex")
      case msg =>
        log.info(s"$msg Delivered As Desired")
        sender() ! StreamAck
    }
  }
  val destinationActor = actorSystem.actorOf(Props[DestinationActor](), "ActorAsSink")
  //signature
  //  actorRefWithAck(ref, _ => identity, _ => onInitMessage, Some(ackMessage), onCompleteMessage, onFailureMessage)
  val actorPoweredSink = Sink.actorRefWithBackpressure[Int](
    destinationActor
    , onInitMessage = StreamInit,
    ackMessage = StreamAck,
    onCompleteMessage = StreamComplete,
    onFailureMessage = throwable => StreamFail(throwable)
  )
  Source(1 to 10).to(actorPoweredSink).run()
  //prints
  //[akka://WithActors/user/ActorAsSink] Stream Initialized
  //[akka://WithActors/user/ActorAsSink] 1 Delivered As Desired
  //[akka://WithActors/user/ActorAsSink] 2 Delivered As Desired
  //[akka://WithActors/user/ActorAsSink] 3 Delivered As Desired
  //................to akka://WithActors/user/ActorAsSink] 10 Delivered As Desired
  //[akka://WithActors/user/ActorAsSink] Stream Complete
}
