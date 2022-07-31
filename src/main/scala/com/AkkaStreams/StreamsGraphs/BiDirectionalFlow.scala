package com.AkkaStreams.StreamsGraphs

import akka.actor.ActorSystem
import akka.stream.{BidiShape, ClosedShape, Materializer}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
//Flow That Goes Both Ways from A --->B && B---->A
object BiDirectionalFlow extends App{
  given actorSystem :ActorSystem = ActorSystem("BiDirectionalFlow")
  given materialize: Materializer = Materializer(actorSystem)
  /*
  * Example: Cryptography*/
  def encrypt(n:Int)(string:String) = string.map(c=>(c+n).toChar)
  def decrypt(n:Int)(string:String) = string.map(c=>(c-n).toChar)
  val biDirectionalStaticGraph = GraphDSL.create(){
    implicit builder=>
    val encryptFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptFlowShape = builder.add(Flow[String].map(decrypt(3)))
    BidiShape.fromFlows(encryptFlowShape,decryptFlowShape)
  }
  val unEncryptedString = List("I'm","Learning","Akka","And","Testing","BiDi","Flows")
  val unEncryptedSource = Source(unEncryptedString)
  val encryptSource = Source(unEncryptedString.map(encrypt(3)))
  val CryptoBiDiGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){
      implicit builder =>
      import GraphDSL.Implicits.*
      val unencryptedSourceShape = builder.add(unEncryptedSource)
      val encryptedSourceShape = builder.add(encryptSource)
      val biDi = builder.add(biDirectionalStaticGraph)
      val encryptedSinkShape = builder.add(Sink.foreach[String](str=>println(s"encrypting....:$str")))
      val decryptedSinkShape = builder.add(Sink.foreach[String](str=>println(s"decrypting....:$str")))
      unencryptedSourceShape ~> biDi.in1 ; biDi.out1 ~> encryptedSinkShape
      decryptedSinkShape <~ biDi.out2 ; biDi.in2 <~ encryptedSourceShape
      ClosedShape
    }
  )
  CryptoBiDiGraph.run()
  /*encrypting....:L*p
  decrypting....:I'm
  encrypting....:Ohduqlqj
  decrypting....:Learning
  encrypting....:Dnnd
  decrypting....:Akka
  encrypting....:Dqg
  decrypting....:And
  encrypting....:Whvwlqj
  decrypting....:Testing
  encrypting....:ElGl
  decrypting....:BiDi
  encrypting....:Iorzv
  decrypting....:Flows
*/
}
