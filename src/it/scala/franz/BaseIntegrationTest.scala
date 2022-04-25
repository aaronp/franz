package franz

import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.{Task, ZIO, ZTraceElement}
//import zio.duration.{Duration, durationInt}
import org.scalatest.Tag
import concurrent.duration.*

import scala.util.Properties

abstract class BaseIntegrationTest extends AnyWordSpec with Matchers with GivenWhenThen with Eventually with ScalaFutures { self =>

  extension (json: String)
    def jason = io.circe.parser.parse(json).toTry.get

  def ensureLocalKafkaRunning(): Unit = EnsureLocalKafkaRunning()

  object IntegrationTest extends Tag("integrationTest")

  given rt: zio.Runtime[Any] = zio.Runtime.global

  def testTimeout: Duration = 30.seconds

  def shortTimeoutJava: Duration = 200.millis

  extension [A](zio: => ZIO[_root_.zio.ZEnv, Any, A])(using rt: _root_.zio.Runtime[_root_.zio.ZEnv])
    def value(): A = rt.unsafeRun(zio.timeout(java.time.Duration.ofMillis(testTimeout.toMillis))).getOrElse(sys.error("Test timeout"))

  extension [A](zio: => Task[A])(using rt: _root_.zio.Runtime[_root_.zio.ZEnv])
    def taskValue(): A = rt.unsafeRun(zio.timeout(java.time.Duration.ofMillis(testTimeout.toMillis))).getOrElse(sys.error("Test timeout"))


  def run[A](zio: => Task[A])(using rt: _root_.zio.Runtime[Any], trace: ZTraceElement): A = rt.unsafeRun(zio.timeout(java.time.Duration.ofMillis(testTimeout.toMillis))).getOrElse(sys.error("Test timeout"))
}
