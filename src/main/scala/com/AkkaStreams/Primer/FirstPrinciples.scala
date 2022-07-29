package com.AkkaStreams.Primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import scala.concurrent.Future
//Goal of akka streams is create:
// asynchronous ,backPressured,Incremental,Potentially Infinite data processing systems
//KNOWN AS REACTIVE STREAMS
//--------------------------------------------------------
//Concepts of Reactive Streams:
//A)Publisher = emits elements (asynchronously) ==>In Akka Called Source [--]-->
//B)Subscriber = receives elements ==>In Akka called Sink  --[-->]
//C)Processor = Transforms elements along the way ==>In Akka called flow --[->  --]-->
// whole data flow looks like this   [--]--> --[->  --]--> --[-->]
//    Directions in Akka Streams
        //-Upstreams mean towards the {{source}} like that direction   [ ]<-----
        //-DownStream means other direction towards the {{sink}}   ---->[ ]
//D)ALl points mentioned above Must be done asynchronously
//E)Concept of back pressure will be discussed later
//{{{{REACTIVE STREAMS IS NOT AN API}}}
//----------------------------------------------------------
object FirstPrinciples extends App{
  implicit val actorsystem: ActorSystem = ActorSystem("FirstPrinciples")
  implicit val materialize:Materializer = Materializer(actorsystem)
  //====>source
  val source: Source[Int, NotUsed] = Source(1 to 10)
  //NotUsed type This type is used in generic type signatures
  // wherever the actual value is of no importance.
  // It is a combination of Scala’s Unit and Java’s Void
  //======>sink
  val sink = Sink.foreach[Int](println)
  //=====>connect them by expression called graph
  val graph: RunnableGraph[NotUsed] = source.to(sink)
  graph.run() // prints 1 to 10
  //short hand one line all the above
  Source(1 to 10).to(Sink.foreach(println)).run() //prints 1 to 10
  //flows Transform elements
  val flow: Flow[Int, Int, NotUsed] = Flow[Int].filter(_%2==0)
  //flow attached to sources via flow returns new sourceWithFlow
  val sourceWithFLow: Source[Int, NotUsed] = source.via(flow)
  //flow to sink
  val flowToSink: Sink[Int, NotUsed] = flow.to(sink)
  source.via(flow).to(sink).run() //prints 2 4 6 8 10
  //or
  sourceWithFLow.to(sink).run() // prints 2 4 6 8 10
  //or
  source.to(flowToSink).run() //prints 2 4 6 8 10
  //----------------------------------------------
  //Note:Sources can emit any kind of object as long as they are immutable and serializable
  //but nulls are not allowed in reactive streams we get nasty NullPointer Exceptions
  //so we use Options instead
  //--------------------------------------------------
  //Various kinds of sources
  //1)finite sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(Set(1,2,3))
  //2)empty source
  val emptySource = Source.empty[String]
  //3)infinite source like lazyList
  val infiniteSource = Source(LazyList.from(1))
  //4)create sources from other things
      //-create sources from futures
  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.future(Future(42))
  //--------------------------------------------------------
  //various types of sinks
  //1)sink the consume anytime and do nothing
  val consumeAllDoNothingSink = Sink.ignore
  //2)iterator sink
  val iteratorSink = Sink.foreach[String](println)
  //3)sink that retrieve certain value like head
  val headSink = Sink.head[Int]
  //4)sink that compute value out of the elements like fold
  val foldSink = Sink.fold[Int,Int](0)((a,b)=>a+b)
  //------------------------------------------------------------
  //various types of flow
  //1)mapped to collection operators
  val mapFlow = Flow[Int].map(_+1)
  //2)transform infinite flow to finite
  val finiteFLow = Flow[Int].take(20)
  //3) we have drop filter etc
  //4)flow doesn't has flatMap
  //but we can achieve that behaviour by passing HOF + Chain them together
  val flatmapFlow  = source.via(mapFlow).via(finiteFLow).to(sink)
  //flatmapFlow.run()
  //short hand syntactic sugar
  val betterFlatmapFlow = Source(1 to 50).map(_*2).take(20).to(Sink.foreach(println))
  betterFlatmapFlow.run()
  //even shorter hand
  Source(1 to 10).map(_*2).take(20).runForeach(println)
  //------------------------------------------------------------
  /**
   * Exercise: create a stream that takes the names of persons, then you will keep the first 2 names with length > 5 characters.
   *
   */
  Source(List("Ahmed","Omar","Mahmoud","essam","Adel","Yehia","Ibrahim","Yasser","Maged","Ali"))
    .filter(_.length>5).take(2).runForeach(println)
}
