package com.AkkaStreams.StreamsGraphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}
//Objectives:
//A)how to incorporate cycles and feedback
//b)understands the dangers of cycles
object CyclicGraphs extends App {
  given actorSystem: ActorSystem = ActorSystem("GraphCycles")

  given materialize: Materializer = Materializer(actorSystem)

  val accelerator = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      val sourceShape = builder.add(Source(1 to 200))
      val mergeShape = builder.add(Merge[Int](2))
      val incrementalShape =
        builder.add(Flow[Int].map { x => println(s"Accelerating $x"); x + 1 })
      sourceShape ~> mergeShape ~> incrementalShape
      mergeShape <~ incrementalShape
      ClosedShape
  }
  //Only Accelerating 1 this behaviour called a cycled deadlock
  //because we always increase the number if the elements in the graph
  //and never get rid of it numbers always in closed cycle inside the graph
  //that components will end up buffering elements so quickly and become rapidly full
  //so everyone start backpressure from the merge to the incremental shape to the merge shape back
  //finally backpressure will stops the entire graph all together from emitting elements
  RunnableGraph.fromGraph(accelerator).run()
  //Solution One : MergePreferred
  //MergePreferred is special version of the merge which have preferential input
  //whenever new element is available the input will take the elements and pass it on
  //regardless of what elements are available on the other outputs
  val correctedAccelerator = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      val sourceShape = builder.add(Source(1 to 200))
      val mergeShape = builder.add(MergePreferred[Int](1))
      val incrementalShape =
        builder.add(Flow[Int].map { x => println(s"Accelerating $x"); x + 1 })
      sourceShape ~> mergeShape ~> incrementalShape
      mergeShape.preferred <~ incrementalShape
      ClosedShape
  }
  RunnableGraph.fromGraph(correctedAccelerator).run()
  //solution 2 using Buffers
  val bufferedRepeater = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      val sourceShape = builder.add(Source(1 to 200))
      val mergeShape = builder.add(Merge[Int](2))
      val repeaterShape =
        builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead)
          .map { x => println(s"Accelerating $x"); Thread.sleep(100); x })
      sourceShape ~> mergeShape ~> repeaterShape
      mergeShape <~ repeaterShape
      ClosedShape
  }
  RunnableGraph.fromGraph(bufferedRepeater).run()
  //-----------------------------------------------------------------------
  /*
  * challenge: create a fan in shape
  * takes two outputs which will fed with exactly one number
  * and output will emit an infinite FIBONACCI SEQUENCE
  * fan In 2 number ==> Zip
  * Infinite use => Merge preferred*/
  //Start with Simple Zip which pack my inputs into tuples ~> MergePreferred ~> flow
  //take my fibonacci tuple and composes an new tuple with the next iteration of fibonacci numbers
  //(last,previous) =>(last+previous ,last) ~> fan-out broadcast one of the broadcast output with take the
  //tuple and only return the biggest element {the fibonacci number} i.e final output
  //the second broadcast output will be feed back into MergePreferred
  //--------------------------------------------------------------------
  //stepOne Create Fibonacci generator static component (zip +mergePreferred+Flow+broadcast+extractor)
  val fibonacciGenerator = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      val zip = builder.add(Zip[BigInt, BigInt]())
      val mergePreferred = builder.add(MergePreferred[(BigInt, BigInt)](1))
      val fibonacciLogicFlow = builder.add(Flow[(BigInt, BigInt)]
        .map {
          pair =>
            val last = pair._1
            val previous = pair._2
            Thread.sleep(100)
            (last+previous,last)
        })
      val broadcast = builder.add(Broadcast[(BigInt,BigInt)](2))
      val extractableFlow = builder.add(Flow[(BigInt,BigInt)].map(_._1))
      zip.out ~>  mergePreferred ~> fibonacciLogicFlow ~> broadcast ~> extractableFlow
                  mergePreferred.preferred    <~          broadcast
      UniformFanInShape(extractableFlow.out,zip.in0,zip.in1)
  }
  //stepTwo Create Fibonacci Runnable Graph {source + Sink+ fibonacci generator}
  val fibonacciRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){
      implicit builder =>
      import GraphDSL.Implicits.*
      val sourceOne = builder.add(Source.single[BigInt](1))
      val sourceTwo = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](println))
      val fibonacciGeneratorComponent = builder.add(fibonacciGenerator)
      sourceOne ~> fibonacciGeneratorComponent.in(0)
      sourceTwo ~> fibonacciGeneratorComponent.in(1)
      fibonacciGeneratorComponent.out ~> sink
      ClosedShape
    }
  )
  fibonacciRunnableGraph.run()
}
