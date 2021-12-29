val scala3Version = "3.1.0"

ThisBuild / scalaVersion  := scala3Version

import sbtwelcome._

logo :=
  s""" 
     |
     |  ____  ___  _____  _  _    ____  ____  __  __  ____  __      __   ____  ____ 
     | (_  _)/ __)(  _  )( !( )  (_  _)( ___)(  !/  )(  _ !(  )    /__! (_  _)( ___)
     |.-_)(  !__ ! )(_)(  )  (     )(   )__)  )    (  )___/ )(__  /(__)!  )(   )__) 
     |!____) (___/(_____)(_)!_)   (__) (____)(_/!/!_)(__)  (____)(__)(__)(__) (____)
     |
     |
     |${scala.Console.GREEN}Json-Template version ${version.value}${scala.Console.RESET}
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
  "junit"                  % "junit"      % "4.13.2"  % Test,
  "org.scalatest"          %% "scalatest" % "3.2.10" % Test,
  "org.pegdown"            % "pegdown"    % "1.6.0" % Test
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "json-template",
    version := "0.0.1-SNAPSHOT",
  )
  .settings(libraryDependencies ++= testDependencies)
  .settings(libraryDependencies += "org.scala-lang" %% "scala3-staging" % "3.1.0")
  .settings(libraryDependencies += "ch.qos.logback" % "logback-core" % "1.2.10")
  .settings(libraryDependencies += ("com.github.aaronp" %% "eie" % "1.0.0").cross(CrossVersion.for3Use2_13))
  .settings(libraryDependencies ++= List("circe-core", "circe-generic", "circe-parser").map(artifact => "io.circe" %% artifact % "0.14.1"))


// lazy val expressions = project
//   .in(file("expressions"))
//   .dependsOn(avroRecords % "test->compile")
//   .settings(name := "expressions", coverageMinimum := 30, coverageFailOnMinimum := true)
//   .settings(commonSettings: _*)
//   .settings(libraryDependencies ++= testDependencies)
//   .settings(libraryDependencies ++= List("circe-core", "circe-generic", "circe-parser").map(artifact => "io.circe" %% artifact % "0.14.1"))
//   .settings(libraryDependencies += ("com.github.aaronp" %% "eie" % "1.0.0").cross(CrossVersion.for3Use2_13))
//   .settings(libraryDependencies ++= List(
//     "org.apache.avro" % "avro"           % "1.10.0",
//     "org.scala-lang" %% "scala3-staging" % "3.1.0"
//   ))

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/
pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/aaronp/json-template</url>
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
        <url>https://github.com/aaronp/json-template</url>
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
