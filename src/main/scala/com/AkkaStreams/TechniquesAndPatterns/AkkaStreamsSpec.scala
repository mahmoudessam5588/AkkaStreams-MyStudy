package com.AkkaStreams.TechniquesAndPatterns

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{Graph, Inlet, Materializer, Outlet, Shape}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class AkkaStreamsSpec extends TestKit(ActorSystem("AkkaStreams"))
  with AnyWordSpecLike with BeforeAndAfterAll {
  override def afterAll(): Unit = {
    given materialize: Materializer = Materializer(system)

    TestKit.shutdownActorSystem(system)
  }

  "A Simple Stream" should {
    "Satisfy Basic Assertion" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)
      val sumFuture: Future[Int] = simpleSource.runWith(simpleSink)
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }
    "Test Actors Integrated With Future Materialized Value" in {
      import akka.pattern.pipe
      import system.dispatcher
      //using testProbe mimicking actor capabilities
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)
      val testProbe = TestProbe()
      //run the stream and given return value is a future I can Pipe the result of the future
      //using akka pattern pipe & execution context
      simpleSource.runWith(simpleSink).pipeTo(testProbe.ref)
      testProbe.expectMsg(55)
    }
    "Integration Testing With Test Actor Based Sink" in {
      val typicalSource = Source(1 to 5)
      //scan works as fold but with every new element
      // the intermediate result will be emitted forward
      //def scan[T](zero: T)(f: (T, Out) => T): Repr[T] = via(Scan(zero, f))
      val flow: Flow[Int, Int, NotUsed] = Flow[Int].scan[Int](0)(_ + _) //0,1,3,6,10,15
      val testingSourceFlow: Source[Int, NotUsed] = typicalSource.via(flow)
      val testProbe: TestProbe = TestProbe()
      //for all these numbers we are going to create a sink as this testProbe
      val probeSink = Sink.actorRef(testProbe.ref,
        onCompleteMessage = println("Success"),
        onFailureMessage = _ => new RuntimeException())
      testingSourceFlow.to(probeSink).run()
      testProbe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }
    //special akka streams test library
    "Integration With Stream Test Kit Sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)
      //plug source with special sink
      val testingSink = TestSink.probe[Int]
      //we will get special value {{TestSubscriber.Probe[Int]}} if we plug them together
      //exposing the materialize value
      val materializeTestValue: TestSubscriber.Probe[Int] = sourceUnderTest.runWith(testingSink)
      //TestSubscriber.Probe[Int] has number of helpful methods
      //request() send a signal to sink to request some demand elements from source under test
      //request and expect have to march numbers of elements in the source
      //expectNext() assertion method Expect and return a stream element.
      //expectComplete() Expect completion Termination of the stream.
      materializeTestValue.request(5).expectNext(2, 4, 6, 8, 10).expectComplete()
    }
    "Integration With Stream Test Kit Source" in {
      import system.dispatcher
      val sinkUnderTest = Sink.foreach[Int] {
        case 13 => throw new RuntimeException("UnDesired")
        case _ =>
      }
      val testingSource: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
      //here we need to test both value so we keep both
      // as a tuple of (TestPublisher.Probe[Int], Future[Done])
      val materializeTestValue: (TestPublisher.Probe[Int], Future[Done]) =
      testingSource.toMat(sinkUnderTest)(Keep.both).run()
      val (testPublisherIntExtractor, futureDoneExtractor) = materializeTestValue
      //we will work on both val above with their special message
      testPublisherIntExtractor
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()
      //assertion on futureDoneExtractor
      futureDoneExtractor.onComplete {
        case Success(_) => fail("The Sink Should Have Thrown An Exception By Now???!!")
        case Failure(_) => //expected result
      }
    }
    "Testing Flow With Source And A Sink tests" in {
      val flowUnderTest = Flow[Int].map(x => x)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]
      val materialize: (TestPublisher.Probe[Int], TestSubscriber.Probe[Int]) =
        testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (testPublisherExtractor, testSubscriberExtractor) = materialize
      //publisher insert element  and subscriber make assertions
      testPublisherExtractor
        .sendNext(1)
        .sendNext(42)
        .sendNext(99)
        .sendNext(100)
        .sendComplete()
      testSubscriberExtractor.request(4)
      testSubscriberExtractor.expectNext(1, 42, 99, 100).expectComplete()
    }

  }
}

