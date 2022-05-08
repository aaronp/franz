import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import franz.SchemaGen.recordForJson
import io.circe.{Encoder, Json}
import org.apache.avro.generic.{GenericRecord, IndexedRecord}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.admin.AdminClient.{DescribeConfigsOptions, ListOffsetsResultInfo, OffsetSpec, TopicDescription, TopicPartition}
import zio.kafka.consumer.CommittableRecord
import zio.kafka.producer.Producer
import zio.{RuntimeConfig, Scope, ZEnv, ZIO}

import java.nio.ByteBuffer
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.jdk.CollectionConverters.*
import scala.util.Try

package object franz {

  /**
    * Supported Kafka types (to convert to/from DynamicJson)
    */
  type Supported = Int | Long | DynamicJson | Json | IndexedRecord | String | ByteBuffer

  type CRecord = CommittableRecord[DynamicJson, DynamicJson]
  type KafkaRecord = ConsumerRecord[DynamicJson, DynamicJson]

  def asLocalTime(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("UTC"))

  /**
    * Some convenience methods on a consumer record (copy methods)
    */
  extension[K, V] (record: ConsumerRecord[K, V]) {
    def withKey[K2](f: K => K2): ConsumerRecord[K2, V] = copyWith {
      case (k, v) => (f(k), v)
    }
    def withValue[V2](f: V => V2): ConsumerRecord[K, V2] = copyWith {
      case (k, v) => (k, f(v))
    }

    def copyWith[K2, V2](f: (K, V) => (K2, V2)): ConsumerRecord[K2, V2] = {
      val (key, value) = f(record.key(), record.value())
      new ConsumerRecord(
        record.topic(),
        record.partition(),
        record.offset(),
        record.timestamp(),
        record.timestampType(),
        -1,
        -1,
        key,
        value,
        record.headers(),
        record.leaderEpoch()
      )
    }

    /** easily be able to convert a consume record into a producer record
      *
      * @param topic     the target topic
      * @param partition the target partition
      * @return a producer record
      */
    def asProducerRecord(topic: String = record.topic(), partition: Int = record.partition()): ProducerRecord[K, V] = {
      ProducerRecord[K, V](
        topic,
        partition,
        record.key(),
        record.value(),
        record.headers()
      )
    }

    def pretty: String =
      s"""${record.topic()} : ${record.partition()}@${record.offset()} {
         |  timestamp : ${asLocalTime(record.timestamp())}
         |  timestampType : ${record.timestampType()}
         |  headers : ${record.headers().iterator().asScala.mkString("[", ",", "]")}
         |  key : ${record.key()}
         |  value : ${record.value()}
         |}""".stripMargin
  }

  extension[K, V] (record: ProducerRecord[K, V]) {
    def pretty: String =
      s"""${record.topic()} : ${record.partition()} {
         |  timestamp : ${asLocalTime(record.timestamp())}
         |  headers : ${record.headers().iterator().asScala.mkString("[", ",", "]")}
         |  key : ${record.key()}
         |  value : ${record.value()}
         |}""".stripMargin
  }


  extension[A: Encoder] (value: A) {
    /**
      * The ability to turn any encodeable value into a sequence of test data via a postFix operation:
      * {{{
      *   val someData : SomeData = ...
      *   val testData: Iterator[Json] = someData.asTestData(10)
      * }}}
      *
      * @param initialSeed
      * @return
      */
    def asTestData(initialSeed: Seed = Seed()): Iterator[Json] = DataGen.repeatFromTemplateIter(value, initialSeed)

  }

  /** "clean up" the ability to execute a ZIO for its value using a given FranzConfig
    */
  extension[E, A] (app: ZIO[Scope & DynamicProducer & BatchedStream, E, A]) {
    def run(config: FranzConfig = FranzConfig()): A = {
      val scoped = app.provideSomeLayer(config.kafkaLayer)
      franz.runtime.unsafeRun(ZIO.scoped(scoped))
    }
  }


  def runtime: zio.Runtime[ZEnv] = zio.Runtime.global.unsafeRun(ZIO.scoped(ZEnv.live.toRuntime(RuntimeConfig.default)))

  extension[A: Encoder] (data: A)
    def asAvro(namespace: String = "namespace"): GenericRecord = recordForJson(Encoder[A].apply(data), namespace)

  extension (json: Json) {
    def asDynamicJson = DynamicJson(json)
  }
  extension (hocon: String) {
    def jason: String = ConfigFactory.parseString(hocon).root().render(ConfigRenderOptions.concise())
    def parseAsJsonTry: Try[Json] = io.circe.parser.parse(jason).toTry
    def parseAsJson: Json = parseAsJsonTry.get
    def parseAsAvro(namespace: String = "namespace"): GenericRecord = parseAsJson.asAvro(namespace)
  }

  extension (admin: zio.kafka.admin.AdminClient) {
    def countByTopic(topics: Iterable[String]): ZIO[Any, Throwable, Map[String, Long]] = {
      offsetsByTopic(topics, OffsetSpec.LatestSpec).map { obtMap =>
        obtMap.foldLeft(Map[String, Long]()) {
          case (map, (topicPartition: TopicPartition, info: ListOffsetsResultInfo)) =>
            val offset: Long = map.getOrElse(topicPartition.name, info.offset)
            map.updated(topicPartition.name, info.offset.max(offset))
        }
      }
    }

    def offsetsByTopic(topics: Iterable[String], spec: OffsetSpec = OffsetSpec.LatestSpec): ZIO[Any, Throwable, Map[TopicPartition, ListOffsetsResultInfo]] = {
      for {
        descByTopic <- admin.describeTopics(topics)
        topicPartitions = descByTopic.values.flatMap { td =>
          td.partitions.map { p =>
            new TopicPartition(td.name, p.partition)
          }
        }
        response <- admin.listOffsets(topicPartitions.map(_ -> spec).toMap)
      } yield response
    }
  }

  extension[T] (x: T | Null) {
    inline def nn: T = {
      assert(x != null)
      x.asInstanceOf[T]
    }
  }
}
