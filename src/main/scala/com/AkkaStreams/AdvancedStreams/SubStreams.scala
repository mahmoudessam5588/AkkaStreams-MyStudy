package com.AkkaStreams.AdvancedStreams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source, SubFlow}
import com.AkkaStreams.AdvancedStreams.SubStreams.wordSource

import scala.concurrent.Future
import scala.util.{Failure, Success}
//Objectives
//A)Create Streams Dynamically
//B)Process SubStreams Uniformly
object SubStreams extends App {
  given actorSystem:ActorSystem = ActorSystem("SubStreams")
  given materialize:Materializer = Materializer(actorSystem)
  //1)Grouping Streams By Certain Function
  val wordSource = Source(List("Mahmoud","Essam","abdel-Hamid","Mahmoud","elMasry"))
  //grouped by return a long complicated type but long story short it acts like source
  val groupedBy: SubFlow[String, NotUsed, wordSource.Repr, RunnableGraph[NotUsed]] =
    wordSource.groupBy(30,w=>if w.isEmpty then '$' else w.toLowerCase().charAt(0))
  //adding sink to it
  //total word count of starting char at index(0)
  groupedBy.to(Sink.fold(0)((count:Int,word:String)=>{
    val totalCount: Int = count+1
    println(s"I Received $word , count is $totalCount")
    totalCount
  })).run()
  /*I Received abdel-Hamid , count is 1
  I Received Essam , count is 1
  I Received Mahmoud , count is 1
  I Received Mahmoud , count is 2
  I Received elMasry , count is 2
*/
  //In A Nutshell:
  //once you attach a consumer{Sink}
  // to sub flow every sub stream will have different materialization concept
  //-----------------------------------------------------------------------
  //2)Merge subStreams Back to single stream
  val listSource = Source(List(
    "I'm Learning Akka",
    "Been Good So Far",
    "Doing Projects Soon"
  ))
  val materializedValueToTalCharCount: Future[Int] = listSource
    .groupBy(2,str=>str.length%2==0)
    .map(_.length)
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_+_))(Keep.right)
    .run()
  materializedValueToTalCharCount.onComplete{
    case Success(value) => println(s"Total Count $value")
    case Failure(exp) => println(s"Failed Operation ${exp.printStackTrace()}")
  }(actorSystem.dispatcher)
  //Total Count 52
  //--------------------------------------------
  //3)Splitting A Stream Into Sub Streams when condition is met then merging
  //splitting large chunks of data when certain condition is met like huge book for example
  val bigText = "Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?"
  val charCountForText: Future[Int] = Source(bigText.toList)
    .splitWhen(_==',')
    .filter(_!=',')
    .map(_=>1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_+_))(Keep.right)
    .run()
  charCountForText.onComplete {
    case Success(value) => println(s"Total Count $value")
    case Failure(exp) => println(s"Failed Operation ${exp.printStackTrace()}")
  }(actorSystem.dispatcher)
  //Total Count 855
  //-----------------------------------------
  //4)Flattening {concat and merge}
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}
