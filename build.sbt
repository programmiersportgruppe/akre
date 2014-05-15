
lazy val scala211CustomBuildSuffix = System.getProperty("scala-2.11.0-custom-build-suffix", "")

lazy val customBuildSuffix = Def.setting(if (scalaVersion.value startsWith "2.11.0") scala211CustomBuildSuffix else "")



name := "akre"

version := "0.7.0" + customBuildSuffix.value

description := "A Redis client for Scala, implemented using Akka."

homepage := Some(url("https://github.com/programmiersportgruppe/akre"))

organization := "org.programmiersportgruppe"



scalacOptions := Seq("-feature", "-deprecation", "-Xfatal-warnings")

crossScalaVersions := Seq("2.10.4", "2.11.0")

scalaVersion := crossScalaVersions.value.head



conflictManager := ConflictManager.strict

dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value



lazy val akkaVersion = Def.setting("2.3.2" + customBuildSuffix.value)

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion.value

libraryDependencies ++= (
  if (scalaVersion.value startsWith "2.11.") Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1")
  else Nil
)



libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.6" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % "test"

// To make IntelliJ's test runner happy
libraryDependencies ++= (
  if (scalaVersion.value startsWith "2.11.") Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.1" % "test")
  else Nil
)
