package franz

import io.circe.Json
import org.apache.avro.generic.IndexedRecord
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}
import zio.Task
import zio.ZIO
import zio.kafka.admin.AdminClient.ListTopicsOptions

import scala.jdk.CollectionConverters.*

object implicits {

  type Supported = Int | Long | Json | IndexedRecord | String

  def asJson(record: IndexedRecord) = GenericRecordToJson(record)

  extension(value: Supported) {

    def asString = value match {
      case x: Int           => x.toString
      case x: Long          => x.toString
      case x: Json          => x.noSpaces
      case x: IndexedRecord => asJson(x).noSpaces
      case x: String        => x
    }
  }

}
