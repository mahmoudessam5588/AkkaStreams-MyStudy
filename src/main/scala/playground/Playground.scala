package playground

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

object Playground extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("QuickStart")
  Source.single("Hello,World").to(Sink.foreach(println)).run()
}
