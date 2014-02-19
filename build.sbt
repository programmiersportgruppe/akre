name := "akre"

organization := "org.programmiersportgruppe"

version := "0.4.0"

scalaVersion := "2.11.0-M8"

crossScalaVersions := Seq("2.10.3", "2.11.0-M7", "2.11.0-M8")

scalacOptions := Seq("-feature", "-deprecation", "-Xfatal-warnings")

publishTo := Some(Resolver.file("published", new File("target/published")))

lazy val scala211CustomBuildSuffix = System.getProperty("scala-2.11.0-custom-build-suffix", "")

libraryDependencies <++= scalaVersion {
  case v if v startsWith "2.11." => Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.0-RC5")
  case _                         => Nil
}

libraryDependencies <+= scalaVersion {
  case "2.11.0-M7" => "com.typesafe.akka" %% "akka-actor" % ("2.3.0-RC1" + scala211CustomBuildSuffix)
  case "2.11.0-M8" => "com.typesafe.akka" %% "akka-actor" % ("2.3.0-RC3" + scala211CustomBuildSuffix)
  case _           => "com.typesafe.akka" %% "akka-actor" % "2.3.0-RC1"
}

libraryDependencies <+= scalaVersion {
  case "2.11.0-M8" => "org.scalatest" %% "scalatest" % "2.1.0-RC2" % "test" intransitive()
  case _           => "org.scalatest" %% "scalatest" % "2.0.1-SNAP4" % "test" intransitive()
}

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test" intransitive() cross CrossVersion.binaryMapped {
  case "2.11.0-M8" => "2.11.0-M3"
  case "2.11.0-M7" => "2.11.0-M3"
  case v => v
}

// To make IntelliJ's test runner happy
libraryDependencies <++= scalaVersion {
  case v if v startsWith "2.11." => Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.0-RC7")
  case _                         => Nil
}
