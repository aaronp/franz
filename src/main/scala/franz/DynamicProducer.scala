package franz

//import zio.blocking.Blocking

import io.circe.Json
import org.apache.avro.generic.{GenericContainer, GenericRecord, IndexedRecord}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.{RIO, Scope, Task, ZIO}

import java.nio.ByteBuffer

object DynamicProducer {
  type Supported = Int | Long | DynamicJson | Json | IndexedRecord | String | ByteBuffer
}

final case class DynamicProducer(producerConfig: FranzConfig = FranzConfig()) {

  import DynamicProducer.*

  val instance: ZIO[Scope, Throwable, Producer]                      = producerConfig.kafkaProducerTask
  private def avroSerde(isKey: Boolean): Task[Serde[Any, Supported]] = producerConfig.serdeSupport.avroSerde(isKey).map(_.asInstanceOf[Serde[Any, Supported]])

  private def parse(jsonString: String) = io.circe.parser.parse(jsonString).toTry.get

  private def serdeForValue[X <: Supported](isKey: Boolean, value: X): Task[Serde[Any, X]] = {
    val task = value match {
      case _: Int  => Task.succeed(Serde.int)
      case _: Long => Task.succeed(Serde.long)
      case _: Json =>
        Task.succeed(Serde.string.inmap[Json](parse) {
          case null => ""
          case a    => a.noSpaces
        })
      case _: DynamicJson =>
        Task.succeed(Serde.string.inmap[DynamicJson](parse.andThen(DynamicJson.apply)) {
          case null => ""
          case a    => a.underlyingJson.noSpaces
        })
      case _: IndexedRecord => avroSerde(isKey)
      case _: String        => Task.succeed(Serde.string)
      case _: ByteBuffer    => Task.succeed(Serde.byteBuffer)
    }
    task.map(_.asInstanceOf[Serde[Any, X]])
  }

  def publishValue[V <: Supported](value: V, topic: String | Null = null): ZIO[Scope, Throwable, RecordMetadata] = {
    val mainTopic = Option(topic).getOrElse(producerConfig.topic)
    for {
      producer: Producer <- instance
      valueSerde         <- serdeForValue[V](false, value)
      r                  <- producer.produce(ProducerRecord(mainTopic, value), valueSerde, valueSerde)
    } yield r
  }

  def publish[K <: Supported, V <: Supported](key: K, value: V, topic: String | Null = null): ZIO[Scope, Throwable, RecordMetadata] = {
    val mainTopic = Option(topic).getOrElse(producerConfig.topic)
    for {
      producer: Producer <- instance
      keySerde           <- serdeForValue[K](true, key)
      valueSerde         <- serdeForValue[V](false, value)
      r                  <- producer.produce(mainTopic, key, value, keySerde, valueSerde)
    } yield r
  }
}