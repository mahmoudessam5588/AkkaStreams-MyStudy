package com.AkkaStreams.AdvancedStreams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, Graph, Inlet, Materializer, Outlet, Shape}

import scala.concurrent.Future
import scala.language.postfixOps
import scala.concurrent.duration.*

//Objectives:
//A)how to create Custom Inputs And Output Components of arbitrary types
//B)Create Different  Generic Balance Components With Different Inlets X Outlets,
//will feed relatively equal number of elements to each of inlets and outlets regardless tot the rate
//of production of inlets
object CustomGraphShapes extends App {
  given actorSystem: ActorSystem = ActorSystem("CustomGraphs")

  given materialize: Materializer = Materializer(actorSystem)

  //the way to define your custom shape by creating your own class + extends Shape
  //each class have Inlets[T],Outlets[T] for the ports
  case class Balance2X3(
                         in0: Inlet[Int],
                         in1: Inlet[Int],
                         out0: Outlet[Int],
                         out1: Outlet[Int],
                         out2: Outlet[Int]) extends Shape {
    //Inlets Outlets signature:
    //-def inlets: immutable.Seq[Inlet[_]]
    override def inlets: Seq[Inlet[_]] = List(in0, in1)

    //-def outlets: immutable.Seq[Outlet[_]]
    override def outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    //Deep copy Method
    //Create a copy of this Shape object, returning the same type of original type
    //- def deepCopy(): Shape
    override def deepCopy(): Shape = Balance2X3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  //implement the custom shape as static component
  val balance2X3Component = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      //create merge and balance shapes
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](3))
      merge ~> balance
      Balance2X3(
        in0 = merge.in(0),
        in1 = merge.in(1),
        out0 = balance.out(0),
        out1 = balance.out(1),
        out2 = balance.out(2)
      )
  }
  //plug it into runnableGraph
  val balance2X3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        //plug it 2 sources and 3 sinks to balance 2X3 component
        val slowSourceOne = Source(LazyList.from(1)).throttle(1, 1 seconds)
        val fastSourceTwo = Source(LazyList.from(1)).throttle(2, 2 second)

        //create sink method and added to 3 sinks to monitor element and incrementing count
        def createSink(index: Int): Sink[Int, Future[Int]] = Sink.fold(0)((count: Int, element: Int) => {
          println(s"Sink Num:$index , Received $element , Count Num: $count")
          count + 1
        })

        val sinkOne = builder.add(createSink(1))
        val sinkTwo = builder.add(createSink(2))
        val sinkThree = builder.add(createSink(3))
        val balance2X3 = builder.add(balance2X3Component)
        slowSourceOne ~> balance2X3.in0
        fastSourceTwo ~> balance2X3.in1
        balance2X3.out0 ~> sinkOne
        balance2X3.out1 ~> sinkTwo
        balance2X3.out2 ~> sinkThree
        ClosedShape
    }
  )
  balance2X3Graph.run()
  /*
  Sink Num:1 , Received 1 , Count Num: 0
  Sink Num:2 , Received 1 , Count Num: 0
  Sink Num:3 , Received 2 , Count Num: 0
  Sink Num:1 , Received 2 , Count Num: 1
  Sink Num:2 , Received 3 , Count Num: 1
  Sink Num:3 , Received 3 , Count Num: 1
  Sink Num:1 , Received 4 , Count Num: 2
  Sink Num:2 , Received 4 , Count Num: 2
  Sink Num:3 , Received 5 , Count Num: 2
  Sink Num:1 , Received 5 , Count Num: 3
  Sink Num:2 , Received 6 , Count Num: 3
  Sink Num:3 , Received 6 , Count Num: 3
  */

  /**
   * Exercise: generalize the balance component, make it M x N
   */
  case class BalanceMXN[T](override val inlets: List[Inlet[T]], override val outlets: List[Outlet[T]])
    extends Shape {
    override def deepCopy(): Shape = BalanceMXN(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
  }

  object BalanceMXN {
    def apply[T](inputCount: Int, outputCount: Int): Graph[BalanceMXN[T], NotUsed] = {
      GraphDSL.create() {
        implicit builder =>
          import GraphDSL.Implicits.*
          val merge = builder.add(Merge[T](inputCount))
          val balance = builder.add(Balance[T](outputCount))
          merge ~> balance
          BalanceMXN(merge.inlets.toList, balance.outlets.toList)
      }
    }
  }

  val balanceMXNGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        val slowSourceOne = Source(LazyList.from(1)).throttle(1, 1 seconds)
        val fastSourceTwo = Source(LazyList.from(1)).throttle(2, 2 second)

        def createSink(index: Int): Sink[Int, Future[Int]] = Sink.fold(0)((count: Int, element: Int) => {
          println(s"Sink Num:$index , Received $element , Count Num: $count")
          count + 1
        })

        val sinkOne = builder.add(createSink(1))
        val sinkTwo = builder.add(createSink(2))
        val sinkThree = builder.add(createSink(3))
        val balance2x3 = builder.add(BalanceMXN[Int](2, 3))

        //noinspection ZeroIndexToHead
        slowSourceOne ~> balance2x3.inlets(0)
        fastSourceTwo ~> balance2x3.inlets(1)

        //noinspection ZeroIndexToHead
        balance2x3.outlets(0) ~> sinkOne
        balance2x3.outlets(1) ~> sinkTwo
        balance2x3.outlets(2) ~> sinkThree
        ClosedShape
    }
  )
  balanceMXNGraph.run()
}
