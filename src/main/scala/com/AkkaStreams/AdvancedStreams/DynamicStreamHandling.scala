package com.AkkaStreams.AdvancedStreams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{FlowShape, Graph, KillSwitches, Materializer, SharedKillSwitch, UniqueKillSwitch}

import scala.concurrent.duration.*
import scala.language.postfixOps
//Objectives:
//A)How To Stop Or Abort A Stream At Runtime
//B)Dynamically Add Fan-In/Fan-Out Branches
object DynamicStreamHandling extends App {
  given actorSystem:ActorSystem = ActorSystem("DynamicStreamHandling")
  given materialize :Materializer = Materializer(actorSystem)
  //stopping streams on purpose by
  //A) Kill Switch:
  // -Is Special Kind Of FLow That Emits The Same Elements That Go Through it But Materializes
  // to a special value that has some additional methods
  //KillSwitches Materializes Special Value {{ Graph[FlowShape[Int, Int], UniqueKillSwitch]}}
  //Kill Switch Single killing Single Stream
  //There Is also shared Kill Switch
  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int]
  //create source and sink
  val counterSource = Source(LazyList.from(1)).throttle(1 , 1 second).log("Counter")
  val normalSink = Sink.ignore
  val killSwitch: UniqueKillSwitch = counterSource
    .viaMat(killSwitchFlow)(Keep.right)
    .to(normalSink).run()
  //KillSwitch Is Then Handled With Scheduler
  actorSystem.scheduler.scheduleOnce(3 seconds){
    killSwitch.shutdown()
  }(actorSystem.dispatcher)
  //prints
  // [Counter] Element: 1 ,  [Counter] Element: 2 , [Counter] Element: 3
  //then   [Counter] Downstream finished
  //-------------------------------------------------
  //B)THERE IS SHARED KILL SWITCHES THAT KILLS MULTIPLE STREAMS AT ONCE
  val anotherCounter = Source(LazyList.from(1))
    .throttle(1,2 seconds).log("AnotherCounter")
  val sharedKillSwitch: SharedKillSwitch = KillSwitches.shared("StopAllStreams")
  //in shared kill switch we don't need it's materialize value
  counterSource.via(sharedKillSwitch.flow[Int]).runWith(Sink.ignore)
  anotherCounter.via(sharedKillSwitch.flow[Int]).runWith(Sink.ignore)
  actorSystem.scheduler.scheduleOnce(3 seconds){
    sharedKillSwitch.shutdown() //shutdown all streams
  }(actorSystem.dispatcher)
  //-----------------------------------------------------------
  //C)Dynamically Adding Fan-In And Fan-Out To Streams
      //1)Merge Hub
      /*A MergeHub is a special streaming hub that is able to collect streamed elements from a dynamic set of producers.
      It consists of two parts, a Source and a Sink.
      The Source streams the element to a consumer from its merged inputs.
      Once the consumer has been materialized, the Source returns a materialized value
      which is the corresponding Sink.
      This Sink can then be materialized arbitrary many times,
      where each of the new materializations will feed its consumed elements to the original Source.*/
  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  //exposing this dynamic merges materialized value by  plugging into it a sink
  val materializeSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run()
  //from here I can Use this materializeSink into other components that the power of akka streams!!!!
  //in Run Time we dynamically added a fan-in inputs to the consumer
  //WE CAN RUN ANY NUMBER OF GRAPHS TO THIS MATERIALIZE SINK To ANY NUMBER OF TIMES
  Source(1 to 10).runWith(materializeSink)
  //or our predefined counter source
  counterSource.runWith(materializeSink)
    //2)Broadcast Hub
    /*A BroadcastHub is a special streaming hub that is able to broadcast streamed elements to a dynamic set of consumers.
    It consists of two parts, a Sink and a Source. The Sink broadcasts elements from a producer to the actually live consumers it has.
    Once the producer has been materialized, the Sink it feeds into returns a materialized value which is the corresponding Source.
    This Source can be materialized an arbitrary number of times,
    where each of the new materializations will receive their elements from the original Sink.*/
  val dynamicBroadCast: Sink[Int, Source[Int, NotUsed]] = BroadcastHub.sink[Int]
  //exposing this dynamic broadcast materialized value by  plugging into it a source
  val materializeSource: Source[Int, NotUsed] =  Source(1 to 100).runWith(dynamicBroadCast)
  materializeSource.runWith(normalSink)
  materializeSource.runWith(Sink.ignore)
  materializeSource.runWith(Sink.foreach[Int](println))
  //-----------------------------------------------------------------------
  /**
   * Challenge - combine a mergeHub and a broadcastHub.
   *
   * A publisher-subscriber component
   */
  val merge: Source[String, Sink[String, NotUsed]] = MergeHub.source[String]
  val broadcast: Sink[String, Source[String, NotUsed]] = BroadcastHub.sink[String]
  val (publisherMerge,subscriberBroadcast)=merge.toMat(broadcast)(Keep.both).run()
  subscriberBroadcast.runWith(Sink.foreach(x=>println(s"Received $x")))
  subscriberBroadcast.map(str=>str.length).runWith(Sink.foreach(x=>println(s"Got NO:$x")))
  Source(List("Mind","Bend","Concept")).runWith(publisherMerge)
  Source(List("Akka","Still","Amazing")).runWith(publisherMerge)
}
