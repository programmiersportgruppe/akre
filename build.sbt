crossScalaVersions := Seq("2.10.4", "2.11.4")

scalaVersion := crossScalaVersions.value.head


publishMavenStyle := true

publishArtifact := false

useGpg := true

def mavenRepository(isSnapshot: Boolean): Some[MavenRepository] = {
  def nexus = "https://oss.sonatype.org/"
  if (isSnapshot)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishTo := mavenRepository(isSnapshot.value)


lazy val akkaVersion = Def.setting("2.3.7")

lazy val akkaActor = Def.setting("com.typesafe.akka" %% "akka-actor" % akkaVersion.value)


val sharedSettings = Seq[Def.Setting[_]](
  name := "akre-" + name.value,
  homepage := Some(url("https://github.com/programmiersportgruppe/akre")),
  scmInfo := Some(ScmInfo(
    browseUrl   = new URL("https://github.com/programmiersportgruppe/akre"),
    connection  = "scm:git:git@github.com:programmiersportgruppe/akre.git"
  )),
  licenses := Seq("MIT Licence" -> new URL("http://opensource.org/licenses/MIT")),
  organization := "org.programmiersportgruppe.akre",
  version := "0.12.0",
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
    ),
  pomExtra := {
    <developers>
      <developer>
        <id>barnardb</id>
        <name>Ben Barnard</name>
        <url>https://github.com/barnardb</url>
      </developer>
    </developers>
  },
  publishTo := mavenRepository(isSnapshot.value)
)

lazy val core = project
  .settings(sharedSettings: _*)
  .settings(
    description := "Core Redis abstractions for Akre.",
    libraryDependencies <+= akkaActor,  // for `akka.util.ByteString`
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % "test",
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.2" % "test"
  )

def coreDependency = core % "compile->compile;test->test"

lazy val commands = project
  .dependsOn(coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    description := "Scala abstractions for Redis commands.",
    libraryDependencies <+= akkaActor
  )

lazy val protocol = project
  .dependsOn(coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    description := "A RESP (REdis Serialization Protocol) implementation.",
    libraryDependencies ++= (
      if (scalaVersion.value startsWith "2.11.") Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2")
      else Nil
    )
  )

lazy val client = project
  .dependsOn(protocol, commands, coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    description := "A Scala Redis client with pipelining, connection pooling, and a Future-based interface, implemented using Akka."
  )

lazy val fake = project
  .dependsOn(commands, coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    description := "A Redis fake for the JVM, implemented using Akka."
  )
