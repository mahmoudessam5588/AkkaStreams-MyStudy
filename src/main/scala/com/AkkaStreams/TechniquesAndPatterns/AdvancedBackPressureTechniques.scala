package com.AkkaStreams.TechniquesAndPatterns

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import java.util.Date
import scala.language.postfixOps
//Objectives:
//A)Recap How to use Buffers And Rates To Control FLow Of The Elements Inside Of Akka Stream
//B)How To Cope With  BackPressure In  Rapid Source That Can't Be Back Pressured
//c)How To Deal With Fast Consumer With Extrapolating/Expanding
object AdvancedBackPressureTechniques extends App {
  given actorsystem: ActorSystem = ActorSystem("AdvancedBackPressure")
  given materialize: Materializer = Materializer(actorsystem)
  //recap backpressure ControlledBufferedFlow
  //drop head drop oldest elements to make room for new ones
  val controlledBufferedFlow = Flow[Int].map(_ * 2)
    .buffer(size = 10, overflowStrategy = OverflowStrategy.dropHead)

  //there are some components that can't be respond to back pressure
  //simplified PagerEvent Example
  case class PagerEvent(desc: String, date: Date,nInstances:Int=1/*Default parameter*/)
  case class Notification(email: String, pagerEvent: PagerEvent)
  val eventsData = List(
    PagerEvent("Infra Broke", new Date),
    PagerEvent("Illegal Elements In The Data Pipeline", new Date),
    PagerEvent("Server Down", new Date),
    PagerEvent("Cassandra Down", new Date),
    PagerEvent("Button Doesn't Work", new Date)
  )
  val eventSource = Source(eventsData)
  val onCallEngineer = "mahmoudessam@gmail.com" //fast service for fetching on call emails

  def sendEmail(notification: Notification): Unit =
    println(s"Dear ${notification.email} you have an event ${notification.pagerEvent}")

  val notificationSink = Flow[PagerEvent]
    .map(event => Notification(onCallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))
  //standard normal behaviour 
  eventSource.to(notificationSink).run()
  //----------------------------------------------------------------------------------
  //Slow Consumers {{SinK}}
  //If we assume That Notification Service{send email} or notification sink is very slow for any reason
  //normally notification sequence flow will try back pressure the source
  //that can cause issues on practical side the relevant engineer that should be paged on
  //the fault code alarm may not be paged in time
  //Also timer based sources don't respond to back pressure
  //create send Email Slow
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email} you have an event ${notification.pagerEvent}")
  }
  //if source can't backpressure incoming events one of the solution to aggregate
  //the incoming events and create one single notification when we receive demand from the sink
  //instead of buffering events on our flow we create single notification for multiple events
  //so we can aggregate the pager events and create single notification out of it
  //conflate act like fold
  val aggregateNotificationFlow = Flow[PagerEvent].conflate(
    (event1,event2) =>{
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"you have $nInstances Events That require your attention",new Date,nInstances)
    }).map(resultEvent => Notification(onCallEngineer,resultEvent))
  //these incoming events will be reduced or folded and the result of reduction of all these events
  //will be emitted downstream{to-Sink}only where there is demand
  eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()
  //Dear mahmoudessam@gmail.com you have
  // an event PagerEvent(you have 5 Events That require your attention,Tue Aug 02 17:14:46 EET 2022,5)
  //this is Decoupling alternative to back pressure
  //-----------------------------------------------------------------------------
  //Slow Producers {{Source}} Extrapolate/expand
  //extrapolate is to run of function from the last element emitted from upstream to compute
  //to further elements to be emitted downstream
  import scala.concurrent.duration.*
  val slowCounter = Source(LazyList.from(1)).throttle(1,1 second)
  val rapidConsumerSink = Sink.foreach[Int](println)
  //we insert special flow
  //extrapolate takes an element and return an iterator
  //def extrapolate[U >: Out](extrapolator: U => Iterator[U], initial: Option[U] = None)
  //so in case there is unmet demand from downstream the iterator will start to produce more
  //elements and artificially feed the downstream
  val extrapolateFLow = Flow[Int].extrapolate(element=>Iterator.from(element))
  val extrapolateRepeaterFLow = Flow[Int].extrapolate(element=>Iterator.continually(element))
  //expand work like extrapolate with a twist while the extrapolate create iterator only when
  //there is unMet demand the expand method this iterator at all times
  val extrapolateExpanderFlow = Flow[Int].expand(element=>Iterator.from(element))
  slowCounter.via(extrapolateFLow).to(rapidConsumerSink).run()
}
