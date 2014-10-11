name := "akre"

version := "0.8.3"

description := "A Redis client for Scala, implemented using Akka."

homepage := Some(url("https://github.com/programmiersportgruppe/akre"))

organization := "org.programmiersportgruppe"



scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Xfatal-warnings",
  "-Ywarn-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Ywarn-numeric-widen",
  // possibly more trouble than it's worth:  "-Ywarn-value-discard",
  "-Xfuture") ++ (
    if (scalaVersion.value.startsWith("2.10.")) Nil
    else Seq(
      "-Ywarn-infer-any",
      "-Ywarn-unused",
      "-Ywarn-unused-import")
  )

crossScalaVersions := Seq("2.10.4", "2.11.2")

scalaVersion := crossScalaVersions.value.head



conflictManager := ConflictManager.strict

dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value



lazy val akkaVersion = Def.setting("2.3.6")

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion.value

libraryDependencies ++= (
  if (scalaVersion.value startsWith "2.11.") Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2")
  else Nil
)



libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.2" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % "test"
