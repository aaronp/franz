import sbt.Credentials
import sbt.Keys.{credentials, name, publishTo}
import sbtwelcome._

enablePlugins(GitVersioning)

ThisBuild / scalaVersion := "3.1.2"

logo :=
  s"""
     |                                                                                      
     |                                                                                            
     |    ffffffffffffffff                                                                        
     |   f::::::::::::::::f                                                                       
     |  f::::::::::::::::::f                                                                      
     |  f::::::fffffff:::::f                                                                      
     |  f:::::f       ffffffrrrrr   rrrrrrrrr   aaaaaaaaaaaaa  nnnn  nnnnnnnn    zzzzzzzzzzzzzzzzz
     |  f:::::f             r::::rrr:::::::::r  a::::::::::::a n:::nn::::::::nn  z:::::::::::::::z
     | f:::::::ffffff       r:::::::::::::::::r aaaaaaaaa:::::an::::::::::::::nn z::::::::::::::z 
     | f::::::::::::f       rr::::::rrrrr::::::r         a::::ann:::::::::::::::nzzzzzzzz::::::z  
     | f::::::::::::f        r:::::r     r:::::r  aaaaaaa:::::a  n:::::nnnn:::::n      z::::::z   
     | f:::::::ffffff        r:::::r     rrrrrrraa::::::::::::a  n::::n    n::::n     z::::::z    
     |  f:::::f              r:::::r           a::::aaaa::::::a  n::::n    n::::n    z::::::z     
     |  f:::::f              r:::::r          a::::a    a:::::a  n::::n    n::::n   z::::::z      
     | f:::::::f             r:::::r          a::::a    a:::::a  n::::n    n::::n  z::::::zzzzzzzz
     | f:::::::f             r:::::r          a:::::aaaa::::::a  n::::n    n::::n z::::::::::::::z
     | f:::::::f             r:::::r           a::::::::::aa:::a n::::n    n::::nz:::::::::::::::z
     | fffffffff             rrrrrrr            aaaaaaaaaa  aaaa nnnnnn    nnnnnnzzzzzzzzzzzzzzzzz
     |                                                                                            
     |
     |${scala.Console.GREEN}franz version ${version.value}${scala.Console.RESET}
     |
     |""".stripMargin.replaceAllLiterally("!", "\\")

usefulTasks := Seq(
  UsefulTask("a", "~compile", "Compile with file-watch enabled"),
  UsefulTask("b", "~test", "Test with file-watch enabled"),
  UsefulTask("c", "integration-test", "Runs the integration tests"),
  UsefulTask("d", "release", "Release a new version (assumes you have ~/.sbt/.credentials set up)")
)

// sbt command-line shortcut
addCommandAlias("integration-test", "Integration/testOnly -- -n integrationTest")

lazy val IntegrationTest = config("it").extend(Test)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "integrationTest"), // Exclude integration tests by default (in ScalaTest)
    IntegrationTest / testOptions := Seq.empty // Include integration tests, by nullifying the above option
  )
  // Enable integration tests
  .settings(
    inConfig(IntegrationTest)(Defaults.testTasks)
  )
  .settings(
    logoColor := scala.Console.GREEN,
    name := "franz",
//    scalacOptions += "-Yexplicit-nulls",
    parallelExecution := false,
    Compile / packageBin / packageOptions += Package.ManifestAttributes("git-sha" -> git.gitHeadCommit.value.getOrElse("unknown")),
    git.remoteRepo := s"git@github.com:aaronp/franz.git",
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    libraryDependencies ++= deps.all,
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "confluent" at "https://packages.confluent.io/maven/",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "franz.build",
    autoAPIMappings := true,
    ghpagesNoJekyll := true,
    scalafmtOnCompile := true,
    scalafmtVersion := "1.4.0",
    versionScheme := Some("early-semver"),
    organization := "com.github.aaronp",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    publishMavenStyle := true,
    exportJars := false,
    pomIncludeRepository := (_ => false),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    git.gitTagToVersionNumber := { tag: String =>
      if (tag matches "v?[0-9]+\\..*") {
        Some(tag)
      } else None
    }
  )

// To sync with Maven central, you need to supply the following information:
//Global / pomExtra := {
pomExtra := {
  <url>https://github.com/aaronp/franz</url>
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
        <url>https://github.com/aaronp/franz</url>
      </developer>
    </developers>
}
