package franz

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO

class ProducersTest extends BaseFranzTest {

  "Producers" should {
    "be able to publish values to different topics" in {
      val p = Producers()
      val test = for {
        r1 <- p.publish(1, 2L, "topic-numbers")
        r2 <- p.publish("text", Json.obj("key" -> "value".asJson), "topic-json")
      } yield (r1, r2)

      val x = run(ZIO.scoped(test))
    }
  }
}
