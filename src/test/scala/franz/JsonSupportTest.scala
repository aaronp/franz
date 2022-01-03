package franz

import io.circe.syntax.EncoderOps
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Base64

class JsonSupportTest extends BaseFranzTest {
  "JsonSupport.anyToJson.format" should {
    "work" in {
      val helloWorld = """{ "hello" : "world" }""".jason
      JsonSupport.anyToJson.format("hello") shouldBe "hello".asJson
      JsonSupport.anyToJson.format(helloWorld) shouldBe helloWorld
      JsonSupport.anyToJson.format(SchemaGen.recordForJson(helloWorld)) shouldBe helloWorld
      JsonSupport.anyToJson.format(123) shouldBe 123.asJson

      val base64Text = JsonSupport.anyToString.format(helloWorld.noSpaces.getBytes("UTF-8"))
      val bytes      = Base64.getDecoder.decode(base64Text)
      JsonSupport.anyToJson.format(bytes) shouldBe helloWorld
    }
  }
}
