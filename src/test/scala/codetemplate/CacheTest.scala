package codetemplate

import java.util.concurrent.atomic.AtomicInteger
import scala.util.{Success, Try}

class CacheTest extends BaseTest {

  "Cache.apply" should {
    "only evaluate once per expression" in {
      val check = Map[String, AtomicInteger]()

      def justIncCompiler(script: String) = Try(check.getOrElse(script, new AtomicInteger(0)).incrementAndGet())

      val undertest = Cache(justIncCompiler)

      undertest("some script one") shouldBe Success(1)
      undertest("some script one") shouldBe Success(1)
      undertest("another script") shouldBe Success(1)
      undertest("another script") shouldBe Success(1)
      undertest("another script") shouldBe Success(1)
    }
  }
}
