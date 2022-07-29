ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.3"
val AkkaVersion = "2.6.19"
val scalaTestVersion = "3.2.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion
)


lazy val root = (project in file("."))
  .settings(
    name := "AkkaStreams-MyStudy"
  )
