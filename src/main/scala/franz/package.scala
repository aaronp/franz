import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import franz.SchemaGen.recordForJson
import io.circe.{Encoder, Json}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.kafka.consumer.CommittableRecord

import scala.util.Try
import zio.kafka.consumer.CommittableRecord

package object franz {

  type CRecord     = CommittableRecord[DynamicJson, DynamicJson]
  type KafkaRecord = ConsumerRecord[DynamicJson, DynamicJson]

  extension [A : Encoder](data : A)
    def asAvro(namespace : String = "namespace"): GenericRecord = recordForJson(Encoder[A].apply(data), namespace)

  extension(hocon: String) {
    def jason: String        = ConfigFactory.parseString(hocon).root().render(ConfigRenderOptions.concise())
    def parseAsJsonTry: Try[Json] = io.circe.parser.parse(jason).toTry
    def parseAsJson: Json         = parseAsJsonTry.get
    def parseAsAvro(namespace : String = "namespace"): GenericRecord = parseAsJson.asAvro(namespace)
  }

  extension[T](x: T | Null) {
    inline def nn: T = {
      assert(x != null)
      x.asInstanceOf[T]
    }
  }
}
