import com.typesafe.tools.mima.plugin.MimaKeys
import sbt.impl.GroupArtifactID

crossScalaVersions in Global := Seq("2.10.6", "2.11.8")

scalaVersion in Global := crossScalaVersions.value.head


val previousVersion = settingKey[Option[String]]("The artifact version for MiMa to compare against when checking for binary incompatibilities prior to release.") in GlobalScope

previousVersion := None

val failOnBinaryIncompatibility = settingKey[Boolean]("Whether the release should fail when a binary incompatibility is detected") in GlobalScope

failOnBinaryIncompatibility := true

publishMavenStyle := true

publishArtifact := false

publishTo in Global := Some {
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else                  Opts.resolver.sonatypeStaging
}


lazy val akkaVersion = Def.setting("2.3.16")

lazy val akkaActor = Def.setting("com.typesafe.akka" %% "akka-actor" % akkaVersion.value)


val sharedSettings = Seq[Def.Setting[_]](
  name := "akre-" + name.value,
  homepage := Some(url("https://github.com/programmiersportgruppe/akre")),
  scmInfo := Some(ScmInfo(
    browseUrl   = url("https://github.com/programmiersportgruppe/akre"),
    connection  = "scm:git:git@github.com:programmiersportgruppe/akre.git"
  )),
  licenses := Seq("MIT Licence" -> url("http://opensource.org/licenses/MIT")),
  organization := "org.programmiersportgruppe.akre",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-target:jvm-1.6",
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
    if (scalaBinaryVersion.value == "2.10") Nil
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
  testOptions in Test += Tests.Argument("-oF"),
  autoAPIMappings := true,
  apiMappings ++= {
    def jar(artifact: GroupArtifactID): Option[File] = {
      val reference = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(artifact % "_")
      (for {
        entry <- (fullClasspath in Runtime).value ++ (fullClasspath in Test).value
        module <- entry.get(moduleID.key)
        if module.organization == reference.organization && module.name == reference.name
      } yield entry.data).headOption
    }
    Seq[(GroupArtifactID, sbt.URL)](
      "com.typesafe" %% "config" -> url("http://typesafehub.github.io/config/latest/api/"),
      "com.typesafe.akka" %% "akka-actor" -> url(s"http://doc.akka.io/api/akka/${akkaVersion.value}/")
    ).flatMap { case (lib, url) => jar(lib).map(_ -> url) }.toMap
  },
  MimaKeys.mimaPreviousArtifacts := previousVersion.value.map(v => projectID.value.copy(revision = v, explicitArtifacts = Nil)).toSet,
  MimaKeys.mimaFailOnProblem := failOnBinaryIncompatibility.value
)

lazy val core = project
  .settings(sharedSettings: _*)
  .settings(
    description := "Core Redis abstractions for Akre.",
    libraryDependencies += akkaActor.value,  // for `akka.util.ByteString`
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % "test",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )

def coreDependency = core % "compile->compile;test->test"

lazy val commands = project
  .dependsOn(coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    description := "Scala abstractions for Redis commands.",
    libraryDependencies += akkaActor.value
  )

lazy val protocol = project
  .dependsOn(coreDependency)
  .settings(sharedSettings: _*)
  .settings(
    description := "A RESP (REdis Serialization Protocol) implementation.",
    libraryDependencies ++= (
      if (scalaVersion.value startsWith "2.11.") Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5")
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
