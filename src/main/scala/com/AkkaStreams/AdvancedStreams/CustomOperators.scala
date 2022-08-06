package com.AkkaStreams.AdvancedStreams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

//objectives
//define your own graph operators with their own internal logic
//by mastering graph stage API
object CustomOperators extends App {
  given actorSystem: ActorSystem = ActorSystem("CustomOperators")

  given materialize: Materializer = Materializer(actorSystem)

  //create a custom source emits random numbers until canceled
  //Graph Stage takes type of source shape
  class RandNumberGenerator(maxValue: Int) extends GraphStage[ /*step 0: define the shape*/ SourceShape[Int]] {
    // step 1: define the ports and the component-specific members
    val output: Outlet[Int] = Outlet[Int]("RandomGenerator")
    val rand = new Random()

    // step 2: construct a new shape
    //takes the shape and ports
    override def shape: SourceShape[Int] = SourceShape(output)

    // step 3: create the logic
    //core of graph stage api
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        //Implementing logic here
        //Assigns callbacks for the events for  ports
        setHandler(output, new OutHandler {
          //override callBack
          //onPull will be called if there is demand downstream
          override def onPull(): Unit = {
            //emit a new element
            val nextNumber = rand.nextInt(maxValue)
            //push it out of the port
            push(output, nextNumber)
          }
        })
      }
  }

  //to use this component
  //when we start the graph that means we're materializing our random generator source
  //then the createLogic() method will be called by akka and the object returned and construct
  //the logic
  val randomGeneratorSource = Source.fromGraph(new RandNumberGenerator(100))
  randomGeneratorSource.runWith(Sink.foreach[Int](println))

  //prints
  /*
  0
  19
  12
  49
  43
  25
  30
  0
  6
  13
  18
  5
*/
  // 2 - a custom sink that prints elements in batches of a given size
  class Batches(batchSize: Int) extends GraphStage[SinkShape[Int]] {
    val input: Inlet[Int] = Inlet[Int]("SinkInput")

    override def shape: SinkShape[Int] = SinkShape(input)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        override def preStart(): Unit = {
          //life cycle method send signal from sink upstream to source
          //similar to actor life cycle
          //invoked before any external events are processes
          pull(input)
        }

        val batch = new mutable.Queue[Int]()
        setHandler(input, new InHandler {
          override def onPush(): Unit = {
            val nextElement: Int = grab[Int](input)
            batch.enqueue(nextElement)
            //proving that back pressure works automatically
            //slow consumer back pressure fast producer
            Thread.sleep(500)
            if batch.size >= batchSize
            then println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
            pull(input) //send demand upstream
          }

          override def onUpstreamFinish(): Unit = {
            if batch.nonEmpty
            then println("New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
            println("Stream finished.")
          }
        })
      }
  }

  val batchSink = Sink.fromGraph(new Batches(10))
  randomGeneratorSource.to(batchSink).run()

  //prints
  /*New batch: [64, 86, 18, 22, 96, 64, 97, 2, 66, 16]
  New batch: [11, 24, 85, 82, 22, 66, 98, 83, 98, 28]
  New batch: [0, 35, 59, 27, 67, 0, 77, 58, 27, 18]
  New batch: [83, 57, 7, 5, 12, 4, 91, 32, 87, 72]
  New batch: [0, 85, 60, 69, 3, 6, 17, 22, 74, 77]
  New batch: [3, 37, 22, 47, 86, 72, 12, 80, 24, 3]
  New batch: [23, 96, 61, 21, 52, 16, 73, 50, 62, 86]
*/
  //Important Notice:
  //InHandlers interacts with upstream:
  // A-onPush
  // B-onUpStreamFinish
  // C-onUpStreamFailure
  //input ports can check and retrieve elements:
  //  A-pull =>Signal Demand
  //  B-grab => takes an element
  //  C-cancel => tell Upstream to stop
  //  D-IsAvailable
  //  E-hasBeenPulled
  //  F-isClosed
  //---------------------------------------------------
  //OutHandlers interacts with downstream:
  // A-onPull
  // B-onDownStreamFinish
  //output ports can send elements
  //  A-push => send an element
  //  B-complete => finish a stream
  //  C-fail => terminate stream with exception
  //  D-isAvailable
  // E-isClosed
  //---------------------------------------------------------------------------
  /* Exercise: a custom flow - a simple filter flow
    * - 2 ports: an input port and an output port
  */
  class GenericFilterFlow[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort: Inlet[T] = Inlet[T]("inputs")
    val outPort: Outlet[T] = Outlet[T]("Output")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        setHandler(inPort, new InHandler{
          override def onPush(): Unit = {
            try{
              val nextElement = grab(inPort)
              if predicate(nextElement) then push(outPort,nextElement) else pull(inPort)
            }
            catch {
              case e: Throwable => failStage(e)
            }
          }
        })
        setHandler(outPort,new OutHandler{
          override def onPull(): Unit = {
            pull(inPort)
          }
        })
      }
  }
  val filterGraph = Flow.fromGraph(new GenericFilterFlow[Int](_>70))
  randomGeneratorSource.via(filterGraph).to(batchSink).run()
  //all batches is more than 70
  /*New batch: [94, 72, 79, 98, 97, 73, 86, 80, 74, 80]
  New batch: [80, 95, 99, 85, 93, 81, 73, 97, 92, 85]
  New batch: [85, 71, 99, 77, 85, 93, 74, 96, 91, 75]
*/
  // 3 - a flow that counts the number of elements that go through it
  //with Materialized Value
  class CounterFutureMaterializeFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]]{
    val inPort: Inlet[T] = Inlet[T]("InputPort")
    val outPort: Outlet[T] = Outlet[T]("OutputPort")

    override def shape: FlowShape[T, T] = FlowShape(inPort,outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) =
      {
        val promise = Promise[Int]
        val graphLogic: GraphStageLogic = new GraphStageLogic(shape) {
          var counter = 0
          setHandler(inPort,new InHandler{
            override def onPush(): Unit = {
              val nextElement = grab(inPort)
              counter +=1
              push(outPort,nextElement)
            }

            override def onUpstreamFinish(): Unit = {
              promise.success(counter)
              super.onUpstreamFinish()
            }

            override def onUpstreamFailure(ex: Throwable): Unit = {
              promise.failure(ex)
              super.onUpstreamFailure(ex)
            }
          })
          setHandler(outPort,new OutHandler{
            override def onPull(): Unit = {
              pull(inPort)
            }

            override def onDownstreamFinish(cause: Throwable): Unit = {
              promise.success(counter)
              super.onDownstreamFinish(cause)
            }
          })
        }

        (graphLogic,promise.future)
      }
  }
  val counterFutureMaterializeFLowGraph: Flow[Int, Int, Future[Int]] = Flow.fromGraph(new CounterFutureMaterializeFlow[Int])
  val counterFuture: Future[Int] = Source(1 to 10)
    .viaMat(counterFutureMaterializeFLowGraph)(Keep.right)
    .to(Sink.foreach[Int](println)).run()
  counterFuture.onComplete{
    case Success(count) => println(s"The number of elements passed: $count")
    case Failure(ex) => println(s"Counting the elements failed: $ex")
  }(actorSystem.dispatcher)
  //prints
  /*
  1
  2
  3
  4
  5
  6
  7
  8
  9
  10
  The number of elements passed: 10
*/
}
