package franz

import codetemplate.DynamicJson
import com.typesafe.config.Config
import franz.SerdeSupport.getClass
import io.circe.Json
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.{Deserializer, Serdes, Serializer}
import org.slf4j.LoggerFactory
import zio.kafka.serde
import zio.kafka.serde.Serde
import zio.{RIO, Task, ZIO}

import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object SerdeSupport {

  def avroAsJsonSerde(schemaRegistryClient: SchemaRegistryClient, properties: Map[String, Any], namespace: String, isKey: Boolean): Serde[Any, DynamicJson] = {
    val d     = AvroAsJsonDeserializer(properties, isKey)
    val s     = AvroAsJsonSerializer(schemaRegistryClient, properties, isKey, namespace)
    val serde = zio.kafka.serde.Serde.apply(d)(s)
    serde.inmap[DynamicJson](DynamicJson.apply)(_.value)
  }

  // read from any Avro topic and squirt it out as Json
  case class AvroAsJsonDeserializer(properties: Map[String, Any], isKey: Boolean) extends serde.Deserializer[Any, Json] {
    private val avroDer = new io.confluent.kafka.serializers.KafkaAvroDeserializer
    avroDer.configure(properties.asJava, isKey)

    override def deserialize(topic: String, headers: Headers, data: Array[Byte]): RIO[Any, Json] = {
      avroDer.deserialize(topic, headers, data) match {
        case record: GenericRecord =>
          //RIO(GenericRecordToJson(record))
          ???
        case other => RIO.fail(new Exception(s"Deserialized data:>${Base64.getEncoder.encodeToString(data)}< as $other, when GenericRecord was expected"))
      }
    }
  }

  case class AvroAsJsonSerializer(client: SchemaRegistryClient, properties: Map[String, Any], isKey: Boolean, namespace: String)
      extends serde.Serializer[Any, Json] {
    private val avroSer = new KafkaAvroSerializer(client, properties.asJava)
    avroSer.configure(properties.asJava, isKey)

    override def serialize(topic: String, headers: Headers, value: Json): RIO[Any, Array[Byte]] = {
      //      RIO {
      //        val avroRecord: GenericRecord = SchemaGen.recordForJson(value, namespace)
      //        avroSer.serialize(topic, headers, avroRecord)
      //      }
      ???
    }
  }

}

final case class SerdeSupport(config: FranzConfig) {

  import config.*

  def serdeFor[A](serdeConfig: Config, isKey: Boolean): Task[Serde[Any, A]] = {
    val deserializerName = serdeConfig.getString("deserializer")
    val serializerName   = serdeConfig.getString("serializer")

    deserializerName.toLowerCase match {
      //      case "string" | "strings" => Task.apply(Serde.string.asInstanceOf[Serde[Any, A]])
      //      case "long" | "longs"     => Task(Serde.long.asInstanceOf[Serde[Any, A]])
      case _ =>
        val kafkaDeserializer: Deserializer[A] = instantiate[Deserializer[A]](deserializerName)
        val kafkaSerializer: Serializer[A]     = instantiate[Serializer[A]](serializerName)

        asSerde(kafkaDeserializer, kafkaSerializer, isKey)
    }
  }

  def avroSerde(isKey: Boolean = false): Task[Serde[Any, Object]] = {
    asSerde(
      io.confluent.kafka.serializers.KafkaAvroDeserializer(schemaRegistryClient),
      io.confluent.kafka.serializers.KafkaAvroSerializer(schemaRegistryClient),
      isKey
    )
  }

  def asSerde[A](kafkaDeserializer: Deserializer[A], kafkaSerializer: Serializer[A], isKey: Boolean = false): Task[Serde[Any, A]] = {
    for {
      deserializer: serde.Deserializer[Any, A] <- zio.kafka.serde.Deserializer
        .fromKafkaDeserializer[A](kafkaDeserializer, consumerSettings.properties, isKey)
      serializer: serde.Serializer[Any, A] <- zio.kafka.serde.Serializer.fromKafkaSerializer[A](kafkaSerializer, consumerSettings.properties, isKey)
    } yield {
      val d = deserializer.asTry.map {
        case Success(ok) => ok
        case Failure(err) =>
          LoggerFactory.getLogger(getClass).error(s"Deserialize failed with $err", err)
          throw err
      }
      val s = new serde.Serializer[Any, A] {
        override def serialize(topic: String, headers: Headers, value: A): RIO[Any, Array[Byte]] = {
          try {
            serializer.serialize(topic, headers, value).catchAll { err =>
              LoggerFactory.getLogger(classOf[FranzConfig]).error(s"serializer.serialize threw $err", err)
              ZIO.attempt(
                LoggerFactory.getLogger(classOf[FranzConfig]).error(s"serializer.serialize threw $err", err)
              ) *> ZIO.fail(err)
            }
          } catch {
            case err: Throwable =>
              LoggerFactory.getLogger(classOf[FranzConfig]).error(s"serializer.serialize failed with $err", err)
              throw err
          }
        }
      }
      Serde[Any, A](d)(s)
    }
  }
}
