package com.AkkaStreams.StreamsGraphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}
import scala.language.postfixOps
//write complex akka streams graph
//familiarize with graph dsl
//get familiar with components
//    -fanOut component {Single input Multiple Output}
//    -fanIn component {Multiple Input and One Output}
object BasicGraphs extends App {
  given actorsystem: ActorSystem = ActorSystem("GraphBasics")
  given materialize: Materializer = Materializer(actorsystem)
  //create streams deal with numbers
  //we need 2 hard computation should be evaluated in parallel then tupled and paired together
  val typicalInputSource = Source(1 to 1000)
  val flowIncrementer = Flow[Int].map(_ + 1)
  val flowMultiplier = Flow[Int].map(_ * 2)
  val tupledSink = Sink.foreach[(Int, Int)](println)
  //now we need to construct a complex graph
  //step 1 setting up the fundamentals for the graph
  val customGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { //return shape
      implicit builder: GraphDSL.Builder[NotUsed] => //builder is mutable data structure
        import GraphDSL.Implicits.* //brings nice operators into scope
        //Step 2 add the necessary components
        //    -A)typicalSource will broadcast it's elements to both FLows operators
        //    -B)distribute source equally
        //    -c)two flows outPut will be merged together into single one and paired as tuple
        //    -D)feed int pairedOutput
        val broadcastElements = builder.add(Broadcast[Int](2)) //fanOut Operator
        val zippedOutput = builder.add(Zip[Int, Int]()) //fanIn Operator
        //Step 3 typing up{{feed(~>)}} the components
        typicalInputSource ~> broadcastElements //input part
        broadcastElements.out(0) ~> flowIncrementer ~> zippedOutput.in0 //first Output of broadcast
        broadcastElements.out(1) ~> flowMultiplier ~> zippedOutput.in1 //second Output of broadcast
        //zipped output feed(~>) into sink
        zippedOutput.out ~> tupledSink
        //Step 4 return shape value {{Closed-shape}}
        ClosedShape //Freeze the builder shape
    } //static graph
  ) //runnable graph
  customGraph.run()
  // run the graph and materialize it
  //------------------------------------------------------------------------------
  //exercise 1: feed a source into 2 sinks using broadcast
  val sinkSum = Sink.foreach[Int](x => println(s"Sink Sum:$x"))
  val sinkMultiply = Sink.foreach[Int](x => println(s"Sink Multiply:$x"))
  val exerciseOneGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits.*
        val broadcastMultipleSinks = builder.add(Broadcast[Int](2))
        typicalInputSource ~> broadcastMultipleSinks ~> sinkSum //implicit port numbering
        broadcastMultipleSinks ~> sinkMultiply
        //broadcastMultipleSinks.out(0)~>sinkSum
        //broadcastMultipleSinks.out(1)~>sinkMultiply
        ClosedShape
    }
  )
  exerciseOneGraph.run()
  //----------------------------------------------------------------------
  //exercise 2 see figure fast +slow -> merge (FanIn) ->balance (FanIn) -> feed to two sinks
  import scala.concurrent.duration.*
  val fastSource = typicalInputSource.throttle(5, 1 second)
  val slowSource = typicalInputSource.throttle(2, 1 second)
  val sinkOne = Sink.foreach[Int](x => println(s"Sink Sum:$x"))
  val sinkTwo = Sink.foreach[Int](x => println(s"Sink Multiply:$x"))
  val exerciseTwoGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits.*
        val mergeBuilder = builder.add(Merge[Int](2))
        val balanceBuilder = builder.add(Balance[Int](2))
        fastSource ~> mergeBuilder ~> balanceBuilder ~> sinkOne
        slowSource ~> mergeBuilder ~> balanceBuilder ~> sinkTwo
        ClosedShape
        //Important note Balance take 2 very different sources in very different speeds and
        //evens out the rate of production of the elements between these resources
        //and split them equally between the 2 sinks
    }
  )
  exerciseTwoGraph.run()
  //Non-Linear Components:
  //A)Fan-Out components ==>{Broadcast,Balance}
  //B)Fan-In components ==>{Zip/ZipWith,Merge,Concat}
}
