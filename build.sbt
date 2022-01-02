import sbt.Credentials
import sbt.Keys.{credentials, publishTo, test}
import sbtwelcome._

enablePlugins(GitVersioning)
enablePlugins(GhpagesPlugin)

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "com.github.aaronp"
ThisBuild / scalaVersion  := "3.1.0"
ThisBuild / resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
ThisBuild / releasePublishArtifactsAction := PgpKeys.publishSigned.value
ThisBuild / publishMavenStyle := true
ThisBuild / exportJars := false
ThisBuild / pomIncludeRepository := (_ => false)
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / git.gitTagToVersionNumber := { tag: String =>
  if (tag matches "v?[0-9]+\\..*") {
    Some(tag)
  } else None
}

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
     |${scala.Console.GREEN}code-template version ${version.value}${scala.Console.RESET}
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

lazy val root = project
  .in(file("."))
  .settings(
    name := "code-template",
    //fork := true,
    packageOptions in (Compile, packageBin) += Package.ManifestAttributes("git-sha" -> git.gitHeadCommit.value.getOrElse("unknown")),
    git.remoteRepo := s"git@github.com:aaronp/code-template.git"
  )
  .settings(libraryDependencies += "org.scalatest"  %% "scalatest" % "3.2.10" % Test)
  .settings(libraryDependencies += "org.scala-lang" %% "scala3-staging" % "3.1.0")
  .settings(libraryDependencies += "ch.qos.logback" % "logback-core" % "1.2.10")
  .settings(libraryDependencies += ("com.github.aaronp" %% "eie" % "1.0.0").cross(CrossVersion.for3Use2_13))
  .settings(libraryDependencies ++= List("circe-core", "circe-generic", "circe-parser").map(artifact => "io.circe" %% artifact % "0.14.1"))

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/

// To sync with Maven central, you need to supply the following information:
//Global / pomExtra := {
ThisBuild / pomExtra := {
  <url>https://github.com/aaronp/code-template</url>
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
        <url>https://github.com/aaronp/code-template</url>
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
