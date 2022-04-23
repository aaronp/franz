import sbt._

object deps {
  def zioVersion = "2.0.0-RC5"

  def dockerEnv = ("com.github.aaronp" %% "dockerenv" % "0.6.0" % Test).cross(CrossVersion.for3Use2_13)
      .exclude("com.typesafe.scala-logging", "scala-logging")
      .exclude("com.typesafe.scala-logging", "scala-logging_2.13")
      .exclude("com.github.mpilquist", "simulacrum")

  def typesafeConfig: ModuleID = "com.typesafe" % "config" % "1.4.2"

  //val logback      =
  def logging = List("ch.qos.logback" % "logback-classic" % "1.2.11", "org.slf4j" % "slf4j-api" % "1.7.36")

  def scalaTest = List("org.scalactic" %% "scalactic" % "3.2.11" % Test,
    "org.scalatest" %% "scalatest" % "3.2.11" % Test,
    "org.pegdown" % "pegdown" % "1.6.0" % Test,
    "com.vladsch.flexmark" % "flexmark-all" % "0.64.0" % Test,
    "junit" % "junit" % "4.13.2" % Test)

  def explicitCats = List("cats-core", "cats-kernel").map { art =>
    ("org.typelevel" %% art % "2.6.1").exclude("org.scala-lang", "scala3-library")
  }

  def all: Seq[ModuleID] = scalaTest ++ explicitCats ++ List(
    // aaron stack
    dockerEnv,
    "com.github.aaronp" %% "args4c" % "1.0.1",
    "com.github.aaronp" %% "code-template" % "1.0.0",
    // config
    "com.typesafe" % "config" % "1.4.2",
    // zio
    "dev.zio" %% "zio-interop-cats" % "3.3.0-RC5",
    "dev.zio" %% "zio" % "2.0.0-RC5",
    "dev.zio" %% "zio-streams" % deps.zioVersion,
    "dev.zio" %% "zio-test" % deps.zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % deps.zioVersion % Test,
    // logging
    "ch.qos.logback" % "logback-classic" % "1.2.11",
    // circe
    "io.circe" %% "circe-generic" % "0.14.1",
    "io.circe" %% "circe-parser" % "0.14.1",
    // avro
    "org.apache.avro" % "avro" % "1.11.0",
    // kafka
    "dev.zio"           %% "zio-streams"             % zioVersion,
    "dev.zio"           %% "zio-kafka"               % "2.0.0-M3",
    ("io.confluent"      % "kafka-streams-avro-serde" % "6.2.1")
      .exclude("org.apache.kafka", "kafka-clients"),
  )
}