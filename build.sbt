name := "akre"

organization := "org.programmiersportgruppe"

version := "0.3.0"

scalaVersion := "2.10.3"

crossScalaVersions := Seq("2.10.3", "2.11.0-M7")

scalacOptions := Seq("-feature", "-deprecation", "-Xfatal-warnings")

publishTo := Some(Resolver.file("published", new File("target/published")))

lazy val scala211CustomBuildSuffix = System.getProperty("scala-2.11.0-custom-build-suffix", "")

libraryDependencies <++= scalaVersion {
  case sv if sv startsWith "2.11." => Seq(
    "com.typesafe.akka" %% "akka-actor" % ("2.3.0-RC1" + scala211CustomBuildSuffix),
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.0-RC5")
  case _                          => Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.0-RC1")
}

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.1-SNAP4" % "test" intransitive()

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test" intransitive() cross CrossVersion.binaryMapped {
  case "2.11.0-M7" => "2.11.0-M3"
  case v => v
}
