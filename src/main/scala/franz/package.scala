import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.kafka.consumer.CommittableRecord

import scala.util.Try
import zio.kafka.consumer.CommittableRecord

package object franz {

  type CRecord     = CommittableRecord[DynamicJson, DynamicJson]
  type KafkaRecord = ConsumerRecord[DynamicJson, DynamicJson]

  extension(hocon: String) {
    def jason: String        = ConfigFactory.parseString(hocon).root().render(ConfigRenderOptions.concise())
    def asJsonTry: Try[Json] = io.circe.parser.parse(jason).toTry
    def asJson: Json         = asJsonTry.get
  }

  extension[T](x: T | Null) {
    inline def nn: T = {
      assert(x != null)
      x.asInstanceOf[T]
    }
  }
}
