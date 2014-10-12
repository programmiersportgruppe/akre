name := "akre"

version := "0.10.0"

description := "A Redis client for Scala, implemented using Akka."

homepage := Some(url("https://github.com/programmiersportgruppe/akre"))

organization := "org.programmiersportgruppe"


crossScalaVersions := Seq("2.10.4", "2.11.2")

scalaVersion := crossScalaVersions.value.head


lazy val akkaVersion = Def.setting("2.3.6")

lazy val akkaActor = Def.setting("com.typesafe.akka" %% "akka-actor" % akkaVersion.value)


val sharedSettings = Seq[Def.Setting[_]](
  conflictManager := ConflictManager.strict,
  dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value,
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Xfatal-warnings",
    "-Yclosure-elim",
    "-Ydead-code",
    "-Yno-adapted-args",
    "-Ywarn-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
//    "-Ywarn-value-discard", // possibly more trouble than it's worth
    "-Xfuture") ++ (
    if (scalaVersion.value.startsWith("2.10.")) Nil
    else Seq(
      "-explaintypes",
      "-Yconst-opt",
      "-Ywarn-infer-any",
      "-Ywarn-unused",
      "-Ywarn-unused-import")
    )
)


lazy val core = project
  .settings(sharedSettings: _*)
  .settings(
    libraryDependencies <+= akkaActor,  // for `akka.util.ByteString`
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % "test",
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.2" % "test"
  )

def coreDependency = core % "compile->compile;test->test"

lazy val commands = project
  .dependsOn(coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    libraryDependencies <+= akkaActor
  )

lazy val protocol = project
  .dependsOn(coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    libraryDependencies ++= (
      if (scalaVersion.value startsWith "2.11.") Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2")
      else Nil
    )
  )

lazy val client = project
  .dependsOn(protocol, commands, coreDependency)
  .settings(sharedSettings: _*)

lazy val fake = project
  .dependsOn(commands, coreDependency)
  .settings(sharedSettings: _*)
