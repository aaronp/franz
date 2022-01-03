import com.typesafe.sbt.pgp
import sbt.Credentials
import sbt.Keys.{credentials, publishTo, test}
import sbtwelcome._

enablePlugins(GitVersioning)

logoColor := scala.Console.GREEN
name := "code-template"
fork := true
packageOptions in (Compile, packageBin) += Package.ManifestAttributes("git-sha" -> git.gitHeadCommit.value.getOrElse("unknown"))
git.remoteRepo := s"git@github.com:aaronp/code-template.git"
releasePublishArtifactsAction := PgpKeys.publishSigned.value
libraryDependencies += "org.scalatest"  %% "scalatest"      % "3.2.10" % Test
libraryDependencies += "org.scala-lang" %% "scala3-staging" % "3.1.0"
libraryDependencies += "ch.qos.logback" % "logback-core"    % "1.2.10"
libraryDependencies += ("com.github.aaronp"                                                            %% "eie"    % "1.0.0").cross(CrossVersion.for3Use2_13)
libraryDependencies ++= List("circe-core", "circe-generic", "circe-parser").map(artifact => "io.circe" %% artifact % "0.14.1")
autoAPIMappings := true
ghpagesNoJekyll := true
scalafmtOnCompile := true
scalafmtVersion := "1.4.0"
versionScheme := Some("early-semver")
organization := "com.github.aaronp"
scalaVersion := "3.1.0"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishMavenStyle := true
exportJars := false
pomIncludeRepository := (_ => false)
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
git.gitTagToVersionNumber := { tag: String =>
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
  UsefulTask("b", "~test", "Test with file-watch enabled"),
  UsefulTask("c", "release", "Release a new version (assumes you have ~/.sbt/.credentials set up)")
)

// see https://leonard.io/blog/2017/01/an-in-depth-guide-to-deploying-to-maven-central/

// To sync with Maven central, you need to supply the following information:
//Global / pomExtra := {
pomExtra := {
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
