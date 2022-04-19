package franz

//import zio.blocking.Blocking

import io.circe.Json
import org.apache.avro.generic.{GenericContainer, GenericRecord, IndexedRecord}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.{RIO, Scope, Task, ZIO}

import java.nio.ByteBuffer

object Producers {
  type Supported = Int | Long | Json | IndexedRecord | String | ByteBuffer
}

final case class Producers(config: FranzConfig = FranzConfig()) {

  import Producers.*

  val instance: ZIO[Scope, Throwable, Producer]                      = config.producer
  private def avroSerde(isKey: Boolean): Task[Serde[Any, Supported]] = config.serdeSupport.avroSerde(isKey).map(_.asInstanceOf[Serde[Any, Supported]])

  private def parse(jsonString: String) = io.circe.parser.parse(jsonString).toTry.get

  private def serdeForValue[X <: Supported](isKey: Boolean, value: X): Task[Serde[Any, X]] = {
    val task = value match {
      case _: Int           => Task.succeed(Serde.int)
      case _: Long          => Task.succeed(Serde.long)
      case _: Json          => Task.succeed(Serde.string.inmap[Json](parse)(_.noSpaces))
      case _: IndexedRecord => avroSerde(isKey)
      case _: String        => Task.succeed(Serde.string)
      case _: ByteBuffer    => Task.succeed(Serde.byteBuffer)
    }
    task.map(_.asInstanceOf[Serde[Any, X]])
  }

  def publish[K <: Supported, V <: Supported](key: K, value: V, topic: String | Null = null) : ZIO[Scope, Throwable, RecordMetadata] = {
    val mainTopic = Option(topic).getOrElse(config.topic)
    for {
      producer: Producer <- instance
      keySerde           <- serdeForValue[K](true, key)
      valueSerde         <- serdeForValue[V](false, value)
      r                  <- producer.produce(mainTopic, key, value, keySerde, valueSerde)
    } yield r
  }
}
