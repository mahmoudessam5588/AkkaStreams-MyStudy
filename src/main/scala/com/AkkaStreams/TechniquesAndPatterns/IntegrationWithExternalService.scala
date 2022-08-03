package com.AkkaStreams.TechniquesAndPatterns

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import scala.concurrent.duration._

import java.util.Date
import scala.concurrent.Future
import scala.language.postfixOps

//how To Integrate Akka Streams With Future
//There Are Situations Where Incoming Elements In Streams we need to Invoke
//Some External Services Like A Remote API
object IntegrationWithExternalService extends App {
  given actorSystem: ActorSystem = ActorSystem("IntegratingWithExternalServices")

  given Materialize: Materializer = Materializer(actorSystem)

  given  dispatcher: MessageDispatcher = actorSystem.dispatchers.lookup("dedicated-dispatcher")

  //Async Service can be simplified as a method returning a future
  def genericExternalService[A, B](element: A): Future[B] = ???

  //more realistic example simplified PagerDuty
  //these kind of events are pushed to service continuously via some kind of api that exposed to other people
  //and in the end we get a source
  //we are going to simplify it as a simple source with a list
  case class PagerEvent(application: String, desc: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("Akka Infra", "Infra Broke", new Date),
    PagerEvent("Fast Data PipeLine", "Illegal Elements In The Data Pipeline", new Date),
    PagerEvent("Akka Http", "Server Down", new Date),
    PagerEvent("Akka Persistence", "Cassandra Down", new Date),
    PagerEvent("Laminar UI Library", "Button Doesn't Work", new Date)
  ))

  object PagerService {
    //so PagerEvent will be the full logic of our Pager duty on call service
    //we will have Engineer List Emails
    //Expose an Api Method that will allow somebody else to page an on call engineer
    private val engineers = List("Mahmoud", "Ahmed", "Moustafa")
    private val emails = Map(
      "Mahmoud" -> "mahmoud@gamil.com",
      "Ahmed" -> "Ahmed@gmail.com",
      "Moustafa" -> "Moustafa@gmail.com"
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] = Future {
      val engineerDutyDay: Long = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val availableEngineer: String = engineers(engineerDutyDay.toInt)
      val engineerEmail: String = emails(availableEngineer)
      //page the engineer
      println(s"Sending Pager Email to $engineerEmail a High Priority Notification $pagerEvent")
      Thread.sleep(1000)
      //return the engineer email that was paid
      engineerEmail
    }//(actorSystem.dispatcher) //not recommended practise for mapAsync
  }

  val filteredInfraEvents: Source[PagerEvent, NotUsed] = eventSource.filter(_.application == "Akka Infra")
  /*mapAsync:
  Transform this stream by applying the given function to each of the elements as they pass through this processing step.
  The function returns a Future and the value of that future will be emitted downstream.
  The number of Futures that shall run in parallel is given as the first argument to mapAsyncUnordered.
  Each processed element will be emitted downstream as soon as it is ready, i.e.
  it is possible that the elements are not emitted downstream in the same order as received from upstream.
  If the function f throws an exception or if the Future is completed with failure and the supervision decision is Supervision.
  Stop the stream will be completed with failure.
  If the function f throws an exception or if the Future is completed with failure and the supervision decision is Supervision.
  Resume or Supervision.
  Restart the element is dropped and the stream continues.
  The function f is always invoked on the elements in the order they arrive (even though the result of the futures
  returned by f might be emitted in a different order).
  Adheres to the ActorAttributes.SupervisionStrategy attribute.
  Emits when any of the Futures returned by the provided function complete
  Backpressures when the number of futures reaches the configured parallelism and the downstream backpressures
  Completes when upstream completes and all futures have been completed and all elements have been emitted
  Cancels when downstream cancels*/
  val pagedEngineerEmails: Source[String, NotUsed] =
    filteredInfraEvents.mapAsync(parallelism = 4)(pgEvent => PagerService.processEvent(pgEvent))
  //mapAsync guarantees the relative order of the elements
  val pagedEmailSink: Sink[String, Future[Done]] =
    Sink.foreach[String](email => println(s"Successfully sent notification to $email"))
  pagedEngineerEmails.to(pagedEmailSink).run()
  //prints
  /*Sending Pager Email to mahmoud@gamil.com a High Priority Notification PagerEvent(Akka Infra,Infra Broke,Tue Aug 02 02:51:23 EET 2022)
  Successfully sent notification to mahmoud@gamil.com*/
  //---------------------------------------------------------------------------------
  //Important Notes:
  //Increasing Parallelism can improve the throughput or the performance of akka streams
  //If You Don't Require Ordering Guarantees use mapAsyncUnOrdered version is even faster
  //there are performance consideration when using mapAsync because due to this guarantee mapAsync
  //HAS TO ALWAYS WAIT FOR THE FUTURES TO COMPLETE SO THAT YOU CAN KEEP IT'S ORDER
  //so if one of the future that you run is slow this will slow down the entire stream
  //-------------------------------------------------------------------------------
  //Another Important Consideration For Running Futures On Streams :
  //If you run future in akka stream you should run them in their own execution context {{not on the actor system}}
  //because actorSystem.dispatcher may starve it for threads
  //above we add
  // given  dispatcher: MessageDispatcher = actorSystem.dispatchers.lookup("dedicated-dispatcher")
  //and in application config
  /*
  * dedicated-dispatcher {
    type = Dispatcher
    executor = "thread-pool-executor"
    thread-pool-executor {
      fixed-pool-size = 5
    }
  }*/
  //--------------------------------------------------------------------------
  //Using PagerService Using Actors:
  class PagerServiceActor extends Actor with ActorLogging {
    //so PagerEvent will be the full logic of our Pager duty on call service
    //we will have Engineer List Emails
    //Expose an Api Method that will allow somebody else to page an on call engineer
    private val engineers = List("Mahmoud", "Ahmed", "Moustafa")
    private val emails = Map(
      "Mahmoud" -> "mahmoud@gamil.com",
      "Ahmed" -> "Ahmed@gmail.com",
      "Moustafa" -> "Moustafa@gmail.com"
    )
    //using actors
    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerDutyDay: Long = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val availableEngineer: String = engineers(engineerDutyDay.toInt)
      val engineerEmail: String = emails(availableEngineer)
      //page the engineer
      log.info(s"Sending Pager Email to $engineerEmail a High Priority Notification $pagerEvent")
      Thread.sleep(1000)
      //================================================

      //return the engineer email that was paid
      engineerEmail
    } //(actorSystem.dispatcher) //not recommended practise for mapAsync

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }
  given timeout:Timeout = Timeout(4 seconds)
  val pagerActor = actorSystem.actorOf(Props[PagerServiceActor](),"PagerServiceActor")
  //Instead of calling pager service that process evens lets ask this actor which also returns future
  val alternativePagerEngineerEmails: NotUsed =
    filteredInfraEvents.
      mapAsync[String](parallelism = 4)(event =>(pagerActor ? event).mapTo[String])
      .to(pagedEmailSink).run()
    //prints
    //INFO] [08/02/2022 14:51:30.004] [IntegratingWithExternalServices-akka.actor.default-dispatcher-7] [akka://IntegratingWithExternalServices/user/PagerServiceActor] Sending Pager Email to mahmoud@gamil.com a High Priority Notification PagerEvent(Akka Infra,Infra Broke,Tue Aug 02 14:51:29 EET 2022)
  //Successfully sent notification to mahmoud@gamil.com
}
