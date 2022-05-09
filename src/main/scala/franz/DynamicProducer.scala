package franz

import io.circe.Json
import org.apache.avro.generic.{GenericContainer, GenericRecord, IndexedRecord}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.slf4j.LoggerFactory
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.{Chunk, RIO, Scope, Task, ZIO}

import java.nio.ByteBuffer

final case class DynamicProducerSettings(producerConfig: FranzConfig = FranzConfig()) { self =>

  def producer: ZIO[Scope, Throwable, DynamicProducer] = producerConfig.kafkaProducerTask.map(p => DynamicProducer(self, p))

  def avroSerde(isKey: Boolean): Task[Serde[Any, Supported]] = producerConfig.serdeSupport.avroSerde(isKey).map(_.asInstanceOf[Serde[Any, Supported]])

  def topic = producerConfig.topic
}

/**
  * A data structure which provides convenience methods over a ZIO Kafka Producer
  */
final case class DynamicProducer(settings: DynamicProducerSettings, kafkaProducer: Producer) {

  import DynamicProducer.*

  private def parseJsonOrThrow(jsonString: String): Json = io.circe.parser.parse(jsonString).toTry.get

  private def serdeForValue[X <: Supported](isKey: Boolean, value: X): Task[Serde[Any, X]] = {
    val task = value match {
      case _: Int  => Task.succeed(Serde.int)
      case _: Long => Task.succeed(Serde.long)
      case _: Json =>
        Task.succeed(Serde.string.inmap[Json](parseJsonOrThrow) {
          case null => ""
          case a    => a.noSpaces
        })
      case _: DynamicJson =>
        Task.succeed(Serde.string.inmap[DynamicJson](parseJsonOrThrow.andThen(DynamicJson.apply)) {
          case null => ""
          case a    => a.underlyingJson.noSpaces
        })
      case _: IndexedRecord => settings.avroSerde(isKey)
      case _: String        => Task.succeed(Serde.string)
      case _: ByteBuffer    => Task.succeed(Serde.byteBuffer)
    }
    task.map(_.asInstanceOf[Serde[Any, X]])
  }

  def publishValue[V <: Supported](value: V, topic: String | Null = null): ZIO[Any, Throwable, RecordMetadata] = {
    val mainTopic = Option(topic).getOrElse(settings.topic)
    for {
      serde <- serdeForValue[V](false, value)
      r     <- kafkaProducer.produce(ProducerRecord(mainTopic, value), serde, serde)
    } yield r
  }

  def publishRecord[K <: Supported, V <: Supported](record: ProducerRecord[K, V]) = {
    for {
      keySerde   <- serdeForValue[K](true, record.key())
      valueSerde <- serdeForValue[V](false, record.value())
      job        <- kafkaProducer.produceAsync(record, keySerde, valueSerde)
      result     <- job
    } yield result
  }

  /**
    * batch publish
    * @param records
    * @tparam K
    * @tparam V
    * @return
    */
  def publishRecords[K <: Supported, V <: Supported](records: Iterable[ProducerRecord[K, V]]): ZIO[Any, Throwable, Chunk[RecordMetadata]] =
    publishRecords(Chunk.fromIterable(records))

  def publishRecordValues[V <: Supported](records: Chunk[V], topic: String | Null = null): ZIO[Any, Throwable, Chunk[RecordMetadata]] = {
    publishRecordValuesAndKeys(records, _ => "", topic)
  }
  def publishRecordValuesAndKeys[K <: Supported, V <: Supported](records: Chunk[V],
                                                                 asKey: V => K,
                                                                 topic: String | Null = null): ZIO[Any, Throwable, Chunk[RecordMetadata]] = {
    val mainTopic = Option(topic).getOrElse(settings.topic)
    val producerRecords = records.map { value =>
      val key = asKey(value)
      ProducerRecord[K, V](mainTopic, key, value)
    }
    publishRecords(producerRecords)
  }

  /**
    * batch publish
    * @param records
    * @tparam K
    * @tparam V
    * @return
    */
  def publishRecords[K <: Supported, V <: Supported](records: Chunk[ProducerRecord[K, V]]): ZIO[Any, Throwable, Chunk[RecordMetadata]] = {
    records.headOption match {
      case None => ZIO.succeed(Chunk.empty)
      case Some(head) =>
        for {
          keySerde   <- serdeForValue[K](true, head.key())
          valueSerde <- serdeForValue[V](false, head.value())
          job        <- kafkaProducer.produceChunkAsync(records, keySerde, valueSerde)
          result     <- job
        } yield result
    }
  }

  def publish[K <: Supported, V <: Supported](key: K, value: V, topic: String | Null = null): ZIO[Any, Throwable, RecordMetadata] = {
    val mainTopic = Option(topic).getOrElse(settings.topic)
    for {
      keySerde   <- serdeForValue[K](true, key)
      valueSerde <- serdeForValue[V](false, value)
      r          <- kafkaProducer.produce(mainTopic, key, value, keySerde, valueSerde)
    } yield r
  }
}
