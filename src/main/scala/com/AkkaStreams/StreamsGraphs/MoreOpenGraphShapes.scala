package com.AkkaStreams.StreamsGraphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanOutShape2, Materializer, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}
import java.util.Date
import javax.swing.plaf.FontUIResource
//Objectives:
//More Focus On Fan-In And Fan-Out Operators
//Uniform And NonUniform Fan-In And Fan-Out Operators
object MoreOpenGraphShapes extends App {
  given actorsystem: ActorSystem = ActorSystem("MoreOpenGraphs")
  given materialize: Materializer = Materializer(actorsystem)
  /*
  * Example : Max3 Operators
  * -3 Inputs Of Type Int
  * -push out the maximum of the 3 */
  //see Figure
  //if we had three hypothetical sources that I would compute the max three of the three elements
  //from each of these sources would be to {{zip the first two with max function}} that computes
  //the max out of the two elements and the result would be fed into another {{zip}} with the
  //element from third source and compute the maximum out of that
  //our goal is abstract away  entire logic and create static component that has 3 input and max
  //single output and computes the max three out of the potential sources
  //-----------------------------------------------------------
  //step 1 BoilerPlate
  val maxThreeStaticGraph = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      //step 2 define auxiliary shapes
      val zipWithMaxOne = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
      val zipWithMaxTwo = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
      //step 3 typing them together
      zipWithMaxOne.out ~> zipWithMaxTwo.in0
      //step 4 return the shape has 3 inputs and single output
      UniformFanInShape(zipWithMaxTwo.out, zipWithMaxOne.in0, zipWithMaxOne.in1, zipWithMaxTwo.in1)
  }
  val sourceOne = Source(1 to 10)
  val sourceTwo = Source((1 to 10).map(_ => 5))
  val sourceThree = Source((1 to 10).reverse)
  val maxSink = Sink.foreach[Int](x => println(s"Max is :$x"))
  //create Runnable Graph from Above Data
  //step one
  val maxThreeRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        //step Two
        val maxThreeShape = builder.add(maxThreeStaticGraph)
        //step three  typing things together
        sourceOne ~> maxThreeShape.in(0)
        sourceTwo ~> maxThreeShape.in(1)
        sourceThree ~> maxThreeShape.in(2)
        maxThreeShape.out ~> maxSink
        //Step Four
        ClosedShape
    }
  )
  maxThreeRunnableGraph.run()
  /*
  * Uniform Fan Out Examole
  *Anti Money Laundering Application And we Are Processing Bank Transactions
  * Bank Transaction Become Suspicious Under Certain Conditions
  * Like If Amount Is More Than 10 thousand dollars
  * Components Design:
  * Takes Transactions as Input and has 2 Outputs
  * FirstOutput => gives back the same transaction for bank to process it further (let it go through)
  * SecondOutput => gives back suspicious transaction I.D for further analysis to other systems*/
  //external components
  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)
  val transactionSource = Source(
    List(
      Transaction("24354565", "Mohammed", "Ahmed", 1000, new Date),
      Transaction("165668", "Ibrahim", "Yasser", 2000, new Date),
      Transaction("545652", "Omar", "Aya", 3000, new Date),
      Transaction("768956", "Mona", "Yehia", 102000, new Date)
    )
  )
  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](tnxID => println(s"suspicious transaction ID:$tnxID"))
  //step one
  val suspiciousTxnStaticGraph = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits.*
      //step two define shapes
      val broadcast = builder.add(Broadcast[Transaction](2))
      val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
      val txnIDExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))
      //step three tie shapes
      broadcast.out(0) ~> suspiciousTxnFilter ~> txnIDExtractor
      //return shape
      //FanOutShape2 meaning that shape has outputs of different types
      new FanOutShape2(broadcast.in, broadcast.out(1), txnIDExtractor.out)
  }
  //step one
  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits.*
        //step two define shapes
        val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)
        //step three tying everything
        transactionSource ~> suspiciousTxnShape.in
        suspiciousTxnShape.out0 ~> bankProcessor
        suspiciousTxnShape.out1 ~> suspiciousAnalysisService
        //step four
        ClosedShape
    }
  )
  suspiciousTxnRunnableGraph.run()
  //prints
  /*Transaction(24354565,Mohammed,Ahmed,1000,Sun Jul 31 21:04:05 EET 2022)
  Transaction(165668,Ibrahim,Yasser,2000,Sun Jul 31 21:04:05 EET 2022)
  Transaction(545652,Omar,Aya,3000,Sun Jul 31 21:04:05 EET 2022)
  suspicious transaction ID:768956
  Transaction(768956,Mona,Yehia,102000,Sun Jul 31 21:04:05 EET 2022)*/
}
