import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import franz.SchemaGen.recordForJson
import io.circe.{Encoder, Json}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.ZIO
import zio.kafka.admin.AdminClient.{DescribeConfigsOptions, ListOffsetsResultInfo, OffsetSpec, TopicDescription, TopicPartition}
import zio.kafka.consumer.CommittableRecord

import scala.util.Try

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

  extension (admin : zio.kafka.admin.AdminClient) {
    def countByTopic(topics: Iterable[String]): ZIO[Any, Throwable, Map[String, Long]] = {
      offsetsByTopic(topics, OffsetSpec.LatestSpec).map { obtMap =>
        obtMap.foldLeft(Map[String, Long]()) {
          case (map, (topicPartition: TopicPartition, info : ListOffsetsResultInfo)) =>
            val offset: Long = map.getOrElse(topicPartition.name, info.offset)
            map.updated(topicPartition.name, info.offset.max(offset))
        }
      }
    }

    def offsetsByTopic(topics: Iterable[String], spec : OffsetSpec = OffsetSpec.LatestSpec): ZIO[Any, Throwable, Map[TopicPartition, ListOffsetsResultInfo]] = {
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

  extension[T](x: T | Null) {
    inline def nn: T = {
      assert(x != null)
      x.asInstanceOf[T]
    }
  }
}
