import mill._, scalalib._, publish._

object codetemplate extends SbtModule {
  def millSourcePath = ammonite.ops.pwd
  def scalaVersion = "3.1.0"

  def publishVersion = "0.0.1"

  def ivyDeps = Agg(
    ivy"io.circe::circe-core:0.14.1",
    ivy"io.circe::circe-generic:0.14.1",
    ivy"io.circe::circe-parser:0.14.1",
    ivy"ch.qos.logback:logback-core:1.2.10",
    ivy"com.github.aaronp:eie_2.13:1.0.0", // .cross(CrossVersion.for3Use2_13))
    ivy"org.scala-lang:scala3-staging_3:${scalaVersion()}"
  )

  object test extends Tests {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.10",

      ivy"org.scala-lang:scala3-staging_3:${scalaVersion()}"
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

  def pomSettings = PomSettings(
    description = "code template",
    organization = "com.github.aaronp",
    url = "https://github.com/aaronp/code-template",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("aaronp", "code-template"),
    developers = Seq(
      Developer("Aaron", "Aaron Pritzlaff", "https://aaronpritzlaff.com")
    )
  )

}