import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import franz.SchemaGen.recordForJson
import io.circe.{Encoder, Json}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.admin.AdminClient.{DescribeConfigsOptions, ListOffsetsResultInfo, OffsetSpec, TopicDescription, TopicPartition}
import zio.kafka.consumer.CommittableRecord
import zio.kafka.producer.Producer
import zio.{RuntimeConfig, Scope, ZEnv, ZIO}

import scala.util.Try

package object franz {

  type CRecord = CommittableRecord[DynamicJson, DynamicJson]
  type KafkaRecord = ConsumerRecord[DynamicJson, DynamicJson]

  extension [K,V](record: ConsumerRecord[K, V]) {
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
    def asProducerRecord(topic: String = record.topic(), partition: Int = record.partition()): ProducerRecord[K, V] = {
      ProducerRecord[K, V](
        topic,
        partition,
        record.key(),
        record.value(),
        record.headers()
      )
    }
  }

  extension[A: Encoder] (value: A) {
    def asTestData(num: Int, initialSeed: Seed = Seed()): Seq[Json] = DataGen.repeatFromTemplate(value, num, initialSeed)
  }
  extension[E, A] (app: ZIO[Scope & DynamicProducer & BatchedStream, E, A]) {
    def run(config: FranzConfig = FranzConfig()): A = {
      val scoped = app.provideSomeLayer(config.kafkaLayer)
      franz.runtime.unsafeRun(ZIO.scoped(scoped))
    }
  }


  def runtime: zio.Runtime[ZEnv] = zio.Runtime.global.unsafeRun(ZIO.scoped(ZEnv.live.toRuntime(RuntimeConfig.default)))

  extension[A: Encoder] (data: A)
    def asAvro(namespace: String = "namespace"): GenericRecord = recordForJson(Encoder[A].apply(data), namespace)

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
