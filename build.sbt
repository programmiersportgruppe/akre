name := "akre"

organization := "org.programmiersportgruppe"

version := "0.1.0"

scalaVersion := "2.10.3"

scalacOptions := Seq("-feature")

publishTo := Some(Resolver.file("published", new File("target/published")))

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"
