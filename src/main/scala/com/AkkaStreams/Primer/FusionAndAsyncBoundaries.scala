package com.AkkaStreams.Primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

//Goals:
//A)how stream component can run on the same actor
//B)Introduce Async boundaries between stream components
object FusionAndAsyncBoundaries extends App{
  given actorsystem: ActorSystem = ActorSystem("FirstPrinciples")
  given materialize: Materializer = Materializer(actorsystem)

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // this runs on the SAME ACTOR an called operator/component FUSION
  simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()
  //all the above is equivalent to code below
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        // flow operations
        val x2 = x + 1
        val y = x2 * 10
        // sink operation
        println(y)
    }
  }
  val simpleActor = actorsystem.actorOf(Props[SimpleActor]())
  (1 to 1000).foreach(simpleActor ! _)
  //Important Note
  //when operations inside the actor streams components are quick this operator fusion is good
  //but operators fusion can cause more harm than good if operations are time expensive
  //lets introduce complex operators flow simulate big computation:
  val complexFlowOne = Flow[Int].map { x => Thread.sleep(1000); x + 1 }
  val complexFlowTwo = Flow[Int].map { x => Thread.sleep(1000); x * 2 }
  //simpleSource.via(complexFlowTwo).via(complexFlowOne).to(simpleSink).run()
  //takes long time with interval of 2 seconds between each result printed to the console
  //because both sleeping threads operate on the same actor
  //to be able to operate asynchronously when operators are expensive
  //we run them separately on Parallel different actors
  //where we need to use {{{{async boundary}}}
  simpleSource.via(complexFlowTwo).async //runs on one actor
    .via(complexFlowOne)//runs on different actor
    .to(simpleSink).run() //onr second time different instead of 2 sec
  //-------------------------------------------------------------
  //Important Note:
  //Async Boundaries Contains:
  //    -everything from previous boundaries (if any)
  //    -everything between the previous boundary and this boundary
  //    -communication is done via actor Receive method
  //-----------------------------------------------------------------
  Source(1 to 3)
    .map(element => {
      println(s"Flow A: $element"); element
    }).async
    .map(element => {
      println(s"Flow B: $element"); element
    }).async
    .map(element => {
      println(s"Flow C: $element"); element
    }).async
    .runWith(Sink.ignore)//Sink.Ignore just consumes everyThing
  //before async 1 to 3 printed 3 times in order {{{1 1 1  2 2 2 3 3 3}} Order Guaranteed
  //after async no ordering guarantees but flow of each element is guaranteed
  // flow A always start with 1 ->3  , flow B always start with 1 -> 3 and so on
}
