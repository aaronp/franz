package franz

import io.circe.Json
import org.apache.avro.generic.IndexedRecord
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}

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
  //  extension [K,V](producer: Producer[K,V])
  /**
    * The aim is to be able to publish *any* type as avro
    */
  extension(producer: Producer[String, SpecificRecord]) {
    def app(topic: String, key: Supported, value: Supported) = ???

    def b(topic: String, value: Supported) = ???
    def record[K, V](key: K, value: V)(kEv: K =:= Supported): ProducerRecord[Supported, Supported] = {
      ???
    }
  }

}
