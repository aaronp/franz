package franz

import franz.Deserializers.getClass
import io.circe.Json
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.IndexedRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import org.slf4j.LoggerFactory
import zio.kafka.serde.Deserializer
import zio.{RIO, Task, ZIO}

import scala.util.{Failure, Success}

object Deserializers {

  def apply(schemaRegistryClient: SchemaRegistryClient, props: Map[String, AnyRef], isKey: Boolean): Task[Deserializer[Any, DynamicJson]] = {
    Deserializer.fromKafkaDeserializer(KafkaAvroDeserializer(schemaRegistryClient), props, isKey).map { deserializer =>
      val fromAvro: Deserializer[Any, DynamicJson] = deserializer.mapM {
        case record: IndexedRecord =>
          val jason = GenericRecordToJson(record)
          ZIO.succeed(DynamicJson(jason))
        case other =>
          ZIO.fail(new Exception(s"Expected an avro record, but got $other"))
      }

      val instance = new Deserializer[Any, DynamicJson] {
        val logger = LoggerFactory.getLogger(getClass)

        override def deserialize(topic: String, headers: Headers, dataInput: Array[Byte]) = {
          val data: Array[Byte] = Option(dataInput).getOrElse(Array.empty)
          val src               = classOf[org.apache.kafka.clients.consumer.ConsumerRecord[_, _]].getProtectionDomain.getCodeSource.getLocation
          logger.debug(s"trying avro on '$topic' (isKey: $isKey)  w/ ${data.length} bytes; ${headers}, src: $src")
          fromAvro.deserialize(topic, headers, data).sandbox.either.flatMap {
            case Left(err) =>
              logger.debug(s"Deserialize on '${topic}' (isKey: $isKey) failed with $err", err)
              ZIO.fail(err.squash)
            case Right(result) =>
              logger.debug(s"FRANZ: read from '$topic' (isKey: $isKey) : $result")
              ZIO.succeed(result)
          }
        }
      }

      instance.orElse(fallback())
    }
  }

  def stringAsJson(text: String): Json =
    io.circe.parser.parse(text).toTry.getOrElse(Json.fromString(text))

  private def fromString: Deserializer[Any, Json] = zio.kafka.serde.Deserializer.string.map(stringAsJson)

  private def fromInt: Deserializer[Any, Json] = zio.kafka.serde.Deserializer.int.map(Json.fromInt)

  private def fromLong: Deserializer[Any, Json] = zio.kafka.serde.Deserializer.long.map(Json.fromLong)

  def fallback(): Deserializer[Any, DynamicJson] = {
    fromInt
      .orElse(fromLong)
      .orElse(fromString)
      .map(DynamicJson.apply)
  }
}
