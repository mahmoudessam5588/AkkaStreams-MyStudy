package com.AkkaStreams.TechniquesAndPatterns

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, Materializer, RestartSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.Random

//Objectives:
//React to Failure in streams
object FaultTolerance extends App{
  given actorSystem:ActorSystem = ActorSystem("FaultTolerance")
  given materialize:Materializer = Materializer(actorSystem)
  //A)logging to know what fault happens in your stream
  //we will create a faulty source
  val faultySource = Source(1 to 10).map(e=>if e == 6 then throw new RuntimeException else e)
  //the default level of logging is info and we need debug so we override i from configuration
  //{application.conf}
  faultySource.log("tracking Elements").to(Sink.ignore).run()
  //elements one to five have been tracked then element 6 upstream  failed
  //throwing exception terminates the whole stream al upstream operators are cancelled and old downstream
  //operators will be informed by a special message
  //---------------------------------------------------------------------------
  //B)Recovery from exception {Gratefully terminating streams}
  faultySource.recover{
    case _: RuntimeException => Int.MinValue
  }.log("gratefulSource").to(Sink.ignore)//.run()
  //prints one to 5 then MinValue and shutdown Upstream
  //------------------------------------------------------------
  //C)Recover With Another Stream
  //Signature
  //def recoverWithRetries[T >: Out](
  //      attempts: Int,
  //      pf: PartialFunction[Throwable, Graph[SourceShape[T], NotUsed]]): Repr[T] =
  //    via(new RecoverWith(attempts, pf))
  faultySource.recoverWithRetries(attempts = 3,{
    case _:RuntimeException => Source(90 to 99)
  }).log("RecoverWithAnotherSource").to(Sink.ignore).run()
  //1 to 5 then 90 to 99 terminated upstream
  //-----------------------------------------------------------
  //D)BackOff Supervision
  //when actor fails the supervisor tried automatically to restart it after
  //an exponentially increasing delay,also add randomness so if failed actors tried to access
  //a resource at the same time it prevents that behaviour
  val restartSource = RestartSource.onFailuresWithBackoff(
    //minBackoff = 1 second,maxBackoff = 30 seconds,randomFactor = 0.2,maxRestarts = 2{deprecated}
    settings = RestartSettings.apply(
      minBackoff = 2 second ,maxBackoff = 30 seconds , randomFactor = 0.2
    )
  )(()=>{
    //return another source
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(e => if e == randomNumber then throw new RuntimeException else e)
  })
  restartSource.log("restarting BackOff").to(Sink.ignore).run()
  //the anonymous function will be called first when we run it this restart source whatever
  //consumer it has if the value here fails for some reason ==>will be swapped and
  // the back off supervision will kick in after one second after one second this function will be
  //called again and the new result will be plugged into you that consumer if that one fails as well
  //then the back interval will be doubled
  //---------------------------------------------------------------------------
  //E)Supervision Strategy
  //unlike actor akka streams are not automatically subject to supervision strategy but must
  //explicitly be documented to support them
  val numbers = Source(1 to 20).map(n=>if n ==15 then throw RuntimeException() else n).log("supervision")
  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy{
    /*Resume = skip faulty element
    * Stop = Stop the stream
    *  Restart = resume + clears internal state*/
    case _ : RuntimeException => Resume
    case  _  => Stop
  })
  supervisedNumbers.to(Sink.ignore).run()
  //prints one to 14 skips 15 and continue from 16 to 20
}
