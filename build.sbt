name := "akre"

organization := "org.programmiersportgruppe"

version := "0.1.0"

scalaVersion := "2.10.3"

crossScalaVersions := Seq("2.10.3", "2.11.0-M7")

scalacOptions := Seq("-feature")

publishTo := Some(Resolver.file("published", new File("target/published")))

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test" intransitive() cross CrossVersion.binaryMapped {
  case "2.11.0-M7" => "2.11.0-M3"
  case v => v
}

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.1-SNAP4" intransitive()

libraryDependencies <++= scalaVersion {
  case sv if sv startsWith "2.11" => Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.0-RC5")
  case _                          => Nil
}
