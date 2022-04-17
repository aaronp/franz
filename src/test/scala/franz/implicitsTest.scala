package franz

import org.scalatest.wordspec.AnyWordSpec
import implicits.*

class implicitsTest extends BaseFranzTest {

  "implicits" should {
    "as 'asString' to any supported type" in {
      2.asString shouldBe "2"

    }
  }
}
