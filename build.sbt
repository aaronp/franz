val scala3Version = "3.1.0"

ThisBuild / scalaVersion  := scala3Version
//ThisBuild / versionScheme := Some("early-semver")

import sbt.Credentials
import sbt.Keys.{credentials, publishTo, test}
import sbtwelcome._

logo :=
  s"""                _             _                       _       _
     |               | |           | |                     | |     | |
     |   ___ ___   __| | ___ ______| |_ ___ _ __ ___  _ __ | | __ _| |_ ___
     |  / __/ _ ! / _` |/ _ !______| __/ _ ! '_ ` _ !| '_ !| |/ _` | __/ _ !
     | | (_| (_) | (_| |  __/      | ||  __/ | | | | | |_) | | (_| | ||  __/
     |  !___!___/ !__,_|!___|       !__!___|_| |_| |_| .__/|_|!__,_|!__!___|
     |                                               | |
     |                                               |_|
     |
     |${scala.Console.GREEN}codetemplate version ${version.value}${scala.Console.RESET}
     |
     |""".stripMargin.replaceAllLiterally("!", "\\")

usefulTasks := Seq(
  UsefulTask("a", "~compile", "Compile with file-watch enabled"),
  UsefulTask("b", "fmt", "Run scalafmt on the entire project"),
  UsefulTask("c", "docs/mdoc", "create documentation"),
  UsefulTask("d", "docs/docusaurusPublishGhpages", "publish documentation"),
  UsefulTask("e", "publishLocal", "Publish the sbt plugin locally so that you can consume it from a different project"),
  UsefulTask("f", "startDocusaurus", "Start Docusaurus"),
)

logoColor := scala.Console.GREEN

val testDependencies = List(
  "org.scalatest"          %% "scalatest" % "3.2.10" % Test,
)


ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
ThisBuild / releasePublishArtifactsAction := PgpKeys.publishSigned.value
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

ThisBuild / publishMavenStyle := true
ThisBuild / pomIncludeRepository := (_ => false)

lazy val root = project
  .in(file("."))
  .settings(
    name := "codetemplate",
//    version := "0.0.1-SNAPSHOT",
    fork := true
  )
  .settings(libraryDependencies ++= testDependencies)
  .settings(libraryDependencies += "org.scala-lang" %% "scala3-staging" % "3.1.0")
  .settings(libraryDependencies += "ch.qos.logback" % "logback-core" % "1.2.10")
  .settings(libraryDependencies += ("com.github.aaronp" %% "eie" % "1.0.0").cross(CrossVersion.for3Use2_13))
  .settings(libraryDependencies ++= List("circe-core", "circe-generic", "circe-parser").map(artifact => "io.circe" %% artifact % "0.14.1"))

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
Global / pomExtra := {
  <url>https://github.com/aaronp/codetemplate</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>aaronp</id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/aaronp/codetemplate</url>
      </developer>
    </developers>
}

lazy val docs = project       // new documentation project
  .in(file("site")) // important: it must not be docs/
  .dependsOn(root)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(
    mdocVariables := Map("VERSION" -> version.value),
    moduleName := "site",
    mdocOut := baseDirectory.value.toPath.resolve("src/pages").toFile
  )

lazy val startDocusaurus = taskKey[String]("Builds the client").withRank(KeyRanks.APlusTask)

startDocusaurus := {
  import sys.process._
  val workDir = new java.io.File("site")
  val output  = sys.process.Process(Seq("npx", "docusaurus", "start"), workDir).!!
  java.awt.Desktop.getDesktop.browse(new URI("http://localhost:3000/index.html"))
  sLog.value.info(output)
  output
}
