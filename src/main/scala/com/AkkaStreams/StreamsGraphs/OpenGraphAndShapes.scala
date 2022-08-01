package com.AkkaStreams.StreamsGraphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FlowShape, Materializer, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source}
//Open Graph Shapes And Complex Open Components
object OpenGraphAndShapes extends App {
  given actorsystem: ActorSystem = ActorSystem("OpenGraphs")
  given materialize: Materializer = Materializer(actorsystem)
  /*composite source that concatenates 2 sources
  * emit all the elements from the first source
  * then all the elements from the second source
  * */
  val sourceOne = Source(1 to 10)
  val sourceTwo = Source(42 to 1000)
  //step one create custom graph boilerplate
  val compositeGraphSource = Source.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        //step two declaring components
        val concat = builder.add(Concat[Int](2))
        //step three tying them together
        sourceOne ~> concat
        sourceTwo ~> concat
        //step four return shape
        SourceShape(concat.out)
    }
  )
  compositeGraphSource.to(Sink.foreach(println)).run()
  /*complex sink*/
  val sinkOne = Sink.foreach[Int](x => println(s"OutPut SinkOne:$x"))
  val sinkTwo = Sink.foreach[Int](x => println(s"OutPut SinkTwo:$x"))
  //step one
  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        //step two add broadcast
        val broadcast = builder.add(Broadcast[Int](2))
        //step three tying them together
        broadcast ~> sinkOne
        broadcast ~> sinkTwo

        //step four return shape
        SinkShape(broadcast.in)
    }
  )
  sourceOne.to(sinkGraph).run()
  //complex flow
  //composite one flow from 2 flow
  //one adds one to a number
  //one that does number * 10
  val flowOne = Flow[Int].map(_ + 1)
  val flowTwo = Flow[Int].map(_ * 10)
  val complexFlow = Flow.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        //everything operates on shape
        //if we don't need auxiliary predefined shapes like broadcast concat etc
        //we create our own shape  like thw following
        val  incrementShape = builder.add(flowOne)
        val multiplierShape = builder.add(flowTwo)
        incrementShape ~> multiplierShape
        FlowShape(incrementShape.in, multiplierShape.out)
    } //static graph
  ) //component
  sourceTwo.via(complexFlow).to(Sink.foreach(println)).run()
  /*Exercise: create a flow from sink and source*/
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create() {
        implicit builder =>
          val sourceShape = builder.add(source)
          val sinkShape = builder.add(sink)
          FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
