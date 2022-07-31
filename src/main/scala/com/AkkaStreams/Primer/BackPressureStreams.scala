package com.AkkaStreams.Primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.language.postfixOps
//Backpressure is one fundamental features of Reactive Streams
//A) elements flow as response to demand from consumers
//      -consumers are the one who triggers the flow of elements through a stream
//      -Consumer Demand From Flow And FLow Demands from Source
//       -If Consumer IS fast all is well
//        -But If consumer is slow then we have a problem because UpStream{{Direction from/to Source}}
//        is Producing elements faster than consumer is able to process them
//        {{If That Happened Special Protocol Will Kick In}}Consumer will send Demand signal Upstream
//         To Flow And Source To Slow Down and When Consumer Increase It's Rate It sends Demand
//          Signal Again To Increase It's Rate
// BACKPRESSURE IS TRANSPARENT and we can control it
object BackPressureStreams extends App {
  given actorsystem: ActorSystem = ActorSystem("BackPressure")
  given materialize: Materializer = Materializer(actorsystem)

  val rapidSource: Source[Int, NotUsed] = Source[Int](1 to 1000)
  val slowSink = Sink.foreach{x=>Thread.sleep(1000);println(s"Sink:$x")}
  rapidSource.to(slowSink).run() //each element will be printed every one second
  // but this is fusion not backpressure because it operates on single actor
  rapidSource.async.to(slowSink).run()//actual backpressure we have 2 components operates on different actors
  //Backpressure is transparent no matter what intermediate links you have in your streams graph
  val typicalFlow: Flow[Int, Int, NotUsed] = Flow[Int].map{ x=>println(s"Incoming $x");x*2}
    rapidSource.async. //first actor
    via(typicalFlow).async //second actor
    .to(slowSink).run()//third
  //few sink increment --->then batching incoming numbers from the flow-->then few sink increment
  // that backpressure in action
  //Explanation
  //A)when Sink is slow it send backpressure demand signal to flow to slow down
  //B)flow instead of signal the source it buffer the numbers till it's full of `6 elements {default}
  //C)When flow buffer is full it has no choice but to send back pressure to source
  //D)and wait from more incoming demand from sink
  //---------------------------------------------------------------
  //Components react to BckPressure In Order:
  //A)try to slow down as possible if it can't
  //B)it buffer elements until it's more demand
  //C)drop down elements from the buffer if it overflows
  //D)last resort tear down or kill the whole stream known as Failure
  //-------------------------------------------------------------------------
  //options we have on control buffer flow
  //OverflowStrategy.{dropHead,dropTail,buffer, backPressure}
  //adding additional setting to typicalFlow
  //A)dropHead:
  //      -size of the buffer is 10 if it overflows it drop the head
  //       -Meaning it's drop the oldest buffered element(s) to make room for new one
  val bufferedFlow = typicalFlow.buffer(10,overflowStrategy = OverflowStrategy.dropHead)
  rapidSource.async. //first actor
    via(bufferedFlow).async //second actor
    .to(slowSink).run() //third
  //prints
  //flow delivers first 1 to 16 elements normally no backPressure
  //drops from 17 to 992 keep dropping because sink is too slow then 992 to 1002 buffered(10 elements)
  // then delivered 992 to 1002 to the sink after incrementing it's given elements from(1 to 16)
  //(1 to 16) were the first numbers flow buffered according to it's default capacity but then buffered
  //size change to the next 10 numbers from 992 to 1002
  /*
     1-16: nobody is backpressured
     17-26: flow will buffer, flow will start dropping at the next element
     26-1000: flow will always drop the oldest element
       => 991-1000 => 992 - 1001 => sink
    */
  /*
     overflow strategies:
     - drop head = oldest
     - drop tail = newest
     - drop new = exact element to be added = keeps the buffer <==== Deprecated in akka 2.6.19
      Instead of dropNew we use in Source {{{Source.queue}}}
     - drop the entire buffer
     - backpressure signal
     - fail
    */
  //Back Pressure Centric Method :
  //    -manually trigger back pressure called Throttling
  import scala.concurrent.duration.*
  rapidSource.throttle(2,1 second) //2 elements per second
    .runWith(Sink.foreach(println))
}
