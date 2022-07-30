package com.AkkaStreams.Primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import scala.concurrent.Future
import scala.util.{Failure, Success}
//Materializing is extracting values out of a running streams
//All components and values inside of it of the whole stream is static
//until you invoke run() method
//then we can obtain meaningful value of the stream ==> called materialized value of the graph
//graph is more like blueprint for a stream
//running graphs allocates the right resources{actors,threads,connections,etc}
//-----------------------------------------------------------------------------
//Materialized Value & concepts:
//A)Materializing graph = materializing all the components
//      - each component produce a materialized value when run represented by starts below
//      - [*-]-->[*-]-->[*]
//      -the graph produces a Single materialized values
//      -our job to choose which one to pick
//2) A component can be materialized multiple times
//       -you can reuses the same component in different graphs
//       -different runs = different materialization thus different materialized values
//3)MATERIALIZED VALUE CAN BE ANYTHING
object MaterializingStreams extends App {
  given actorsystem: ActorSystem = ActorSystem("FirstPrinciples")
  given materialize: Materializer = Materializer(actorsystem)
  import actorsystem.dispatcher

  val simpleGraphValue: NotUsed = Source(1 to 10).to(Sink.foreach(println)).run()//means just a Unit
  //so if we need to have a more meaningful value we have to change the sink
  val source: Source[Int, NotUsed] = Source(1 to 20)
  val sink: Sink[Int, Future[Int]] = Sink.reduce[Int](_+_)
  //Sink[Int, Future[Int]] means that after sink has finished receiving elements it will expose
  //A Materialized value {Future[Int]} future will be completed with the sum of all these elements
  //to get a value we start a graph and connect them together
  val futureSum: Future[Int] = source.runWith(sink)
  //then we deal with it as normal future with onComplete method
  futureSum.onComplete{
    case Success(value) => println(s"Sum Of All Elements is $value")
    case Failure(exception) => println(s"Unsuccessful Operation ${exception.printStackTrace()}")
  }
  //prints 210
  //Important Note:
  //when we construct a graph with methods {{{via() , to()}}} when we connect flows ans sinks
  //by default {{THE LEFT MOST MATERIALIZED VALUE IS KEPT}}
  //but we can have further control over which Materialized value we can choose by different methods
  //-------------------------------------------------------------------------------
  //choosing materialized value
  val sourceDemo = Source(1 to 20)
  val flowDemo = Flow[Int].map(_+1)
  val sinkDemo = Sink.foreach(println)
  //viaMat takes the component I want to connect to the source and I can supply another Argument
  //combination function that takes Materialized value of this Component and return third Materialized
  //value which will be the Materialized Value of Composite component
  val graphDemo: RunnableGraph[Future[Done]] =sourceDemo.viaMat(flowDemo)(Keep.right).toMat(sinkDemo)(Keep.right)
  //keep equivalent to ((source,flow)=>flow)
  //we have keep.left ,right and both==>tuple of source and flow ,none =>returns not used
  //toMat() same idea as viaMat
  //graph demo returns RunnableGraph[Future[Done]]
  //lets run the graph next with onComplete()
  graphDemo.run().onComplete{
    case Success(value) => println(s"Print $value After Sum Next Iterable Value")
    case Failure(exception) => println(s"Unsuccessful Operation ${exception.printStackTrace()}")
  }
  //short hand syntactic sugar
  val sum: Future[Int] = Source(1 to 10).runWith(Sink.reduce[Int](_+_)) //signature  toMat(sink)(Keep.right).run()
  //even shorter hand !!!
  val sumReduce: Future[Int] = Source(1 to 10).runReduce[Int](_+_) //signature runWith(Sink.reduce(f))
  //backward operation
  Sink.foreach[Int](println).runWith(Source.single(55))
  //Important Note:
  //Source to Sink keeps the left component Materialized Value
  //Source runWith Sink it uses the Sinks Materialized Value
  //syntactic sugar to run components both way
  val bothWays: (NotUsed, Future[Done]) = Flow[Int].map(_*2).runWith(sourceDemo,sinkDemo)
  //-------------------------------------------------------------------------------------
  /**
   * - return the last element out of a source (use Sink.last)
   * - compute the total word count out of a stream of sentences
   *   - map, fold, reduce
   */
  //Operation On Sink
  val lastElement: Future[Int] = Source(1 to 20).runWith(Sink.last[Int])
  val wordCount: Future[Int] =
    Source(List("I Love Akka","I Love Scala","Mahmoud Essam"))
    .runWith(Sink.fold[Int,String](0)((word,sentence)=>word+sentence.split(" ").length))
   //Operation On Flow
   val wordsCountSource: Source[String, NotUsed] =  Source(List("I Love Akka","I Love Scala","Mahmoud Essam"))
   val wordsCountSink = Sink.foreach(println)
   val wordsFlow: Future[Done] =
     Flow[String].fold[Int](0)((word,sentence)=>word+sentence.split(" ").length)
     .runWith(wordsCountSource,wordsCountSink)._2 //second element of the tuple is what we need
}
