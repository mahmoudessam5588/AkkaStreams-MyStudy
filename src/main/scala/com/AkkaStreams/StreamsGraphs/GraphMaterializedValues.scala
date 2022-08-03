package com.AkkaStreams.StreamsGraphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Materializer, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

//Expose Materialized value In Components built with Graph Dsl
object GraphMaterializedValues extends App {
  given actorsystem: ActorSystem = ActorSystem("MoreOpenGraphs")

  given materialize: Materializer = Materializer(actorsystem)

  val wordSource = Source(List("Scala", "and", "Akka", "are", "Awesome"))
  val printer: Sink[String, Future[Done]] = Sink.foreach[String](println)
  val counter: Sink[String, Future[Int]] = Sink.fold[Int, String](0)((count, _) => count + 1)
  /*
  * A composite Component (sink)
  * -Prints all Strings the are Lower Case
  * -Counts the Strings That Are Short < 5 chars*/
  //Components Design:
  //we will have a Broadcast and one of the Broadcast branches will filter out all strings with
  //lowerCase and feed them to the printer
  //the other branch of the Broadcast will fill only string that are short and feed that into counter
  //Step One
  val complexWordSink: Sink[String, Future[Int]] = Sink.fromGraph(
    //we passing the desired components as High order function on Create Graph To Expose The Materialize Value
    //as shown below
    GraphDSL.createGraph(printer, counter)((_, counterMat) => counterMat) {
      implicit builder =>
        (printerShape, counterShape) =>
          import GraphDSL.Implicits.*
          //Step Two Create Shapes
          val broadCast = builder.add(Broadcast[String](2))
          val printerFLow = builder.add(Flow[String].filter(word => word == word.toLowerCase))
          val counterFlow = builder.add(Flow[String].filter(_.length < 5))
          //step three tying up
          broadCast ~> printerFLow ~> printerShape
          broadCast ~> counterFlow ~> counterShape
          //step four return shape
          SinkShape(broadCast.in)
      //to improve Functionality we want to expose the Materialized value of Count
      //complex wordSink returns Sink[String, NotUsed] NotUsed is Materialized Value of
      //the complexSink we can change that and expose it by passing HOF(higher order function)
      //in create({{HERE}}) like above create(counter) but this changes the whole logic from builder
      //to shape it become HOF between builder and a function that takes the shape of the parameter
      //i have just inserted
    }
  )
  val shortStringCountFuture: Future[Int] = wordSource.runWith(complexWordSink)
  shortStringCountFuture.onComplete {
    case Success(count) => println(s"total word count of short strings is $count")
    case Failure(exception) => println(s"Unsuccessful Operation ${exception.printStackTrace()}")
  }(actorsystem.dispatcher)

  //prints
  /*and
  are
  total word count of short strings is 3*/
  def enhancedFLow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((count, _) => count + 1)
    Flow.fromGraph(
      GraphDSL.createGraph(counterSink) {
        implicit builder =>
          counterSinkShape =>
            import GraphDSL.Implicits.*
            val broadcast = builder.add(Broadcast[B](2))
            val originalFlowShape = builder.add(flow)
            originalFlowShape ~> broadcast ~> counterSinkShape
            FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }

  val typicalSource = Source(1 to 55)
  val typicalFlow = Flow[Int].map(x => x)
  val typicalSink = Sink.ignore
  val enhancedFLowCountFuture: Future[Int] =
    typicalSource.viaMat(enhancedFLow(typicalFlow))(Keep.right).toMat(typicalSink)(Keep.left).run()
  enhancedFLowCountFuture.onComplete {
    case Success(count) => println(s"total sum elements from flow is $count")
    case Failure(exp) => println(s"exception ${exp.printStackTrace()}")
  }(actorsystem.dispatcher)
  //prints
  //total sum elements from flow is 55
}
