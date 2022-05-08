package franz

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.kafka.consumer.CommittableRecord
import zio.stream.{Take, ZStream}
import zio.{Chunk, Ref, Scope, Task, ZIO}

import java.nio.ByteBuffer
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Success

class BatchedStreamTest extends BaseFranzTest {

  val ids: scala.collection.BufferedIterator[String] = eie.io.AlphaCounter.from(System.currentTimeMillis())

  case class Child(value: String, truthy: Boolean)

  case class Parent(name: String, id: Int, kid: Child)

  val testConfig: FranzConfig = FranzConfig()

  "BatchedStream" should {
    "be able to read avro data" in {
      val data = DataGen(Parent("", 0, Child("", true)))

      val avroTopic = s"BatchedStreamTest-${ids.next()}"

      import SchemaGen.*

      // this test publishes a bunch of different types to different topics, then reads 'em
      val test = for {
        dp <- testConfig.dynamicProducer
        _ <- dp.publish("key", data.asAvro("this.is.avro"), avroTopic)
        reader <- testConfig.batchedStream
        headOpt <- reader.withTopic(avroTopic).kafkaStream.runHead
        head <- ZIO.fromOption(headOpt)
        _ <- ZIO.scoped(testConfig.admin.flatMap(_.deleteTopic(avroTopic)))
      } yield head

      val committableRecord: CommittableRecord[DynamicJson, DynamicJson] = rt.unsafeRun(ZIO.scoped(test))

      committableRecord.key.asString shouldBe "key"
      committableRecord.value.as[Parent] shouldBe Success(data.as[Parent].toTry.get)
    }

    "be able to read from a json topic" in {
      val data = DataGen(Parent("", 0, Child("", true)))

      import SchemaGen.*
      val dp = testConfig.dynamicProducer
      import dp.*

      val jsonTopic = s"topic-json-${ids.next()}"

      // this test publishes a bunch of different types to different topics, then reads 'em
      val testCase = for {
        dp <- testConfig.dynamicProducer
        _ <- dp.publish(data, data, jsonTopic)
        reader <- testConfig.batchedStream
        headOpt <- reader.withTopic(jsonTopic).kafkaStream.runHead
        head <- ZIO.fromOption(headOpt)

        /** TODO - here we should use the schema reg client to try and read the schema for our (json) topic, which should fail */
        admin <- testConfig.admin
        _ <- admin.deleteTopic(jsonTopic)
      } yield head

      val record: CommittableRecord[DynamicJson, DynamicJson] = rt.unsafeRun(ZIO.scoped(testCase))

      val key = record.key
      val value = record.value

      value.as[Parent] shouldBe Success(data.as[Parent].toTry.get)
      key shouldBe value
    }

    "be able to read from any topic" in {
      val data = DataGen.repeatFromTemplate(Parent("", 0, Child("", true)), 10).toSeq
      val expected = (data.size * 2) + 1 // two lots of 10 records, as well as a single numeric record

      val avroTopic = s"polypublish-test-avro-${ids.next()}"
      val numTopic = s"polypublish-test-numbers-${ids.next()}"
      val jsonTopic = s"polypublish-test-json-${ids.next()}"

      import SchemaGen.*
      val dp = testConfig.dynamicProducer
      import dp.*

      // this test publishes a bunch of different types to different topics, then reads 'em
      val testCase = for {
        dp <- testConfig.dynamicProducer
        _ <- dp.publish(1, 2L, numTopic)
        _ <- ZIO.foreach(data) { d =>
          dp.publish(d, d.asAvro("this.is.avro"), avroTopic)
        }
        _ <- ZIO.foreach(data) { d =>
          dp.publish("text", d, jsonTopic)
        }
        reader <- testConfig.batchedStream
        queue <- reader.withTopics(avroTopic, numTopic, jsonTopic).kafkaStream.take(expected).toIterator
        takeList = queue.take(expected).toList
        list <- ZIO.foreach(takeList) { either =>
          ZIO.fromEither(either)
        }

        /** TODO - here we should use the schema reg client to try and read the subjects, which should only contain our avro topic */
        admin <- testConfig.admin
        subjects = testConfig.schemaRegistryClient.getAllSubjects.asScala.toList
        _ <- admin.deleteTopic(avroTopic)
        _ <- admin.deleteTopic(numTopic)
        _ <- admin.deleteTopic(jsonTopic)
      } yield (list, subjects)

      val (read, subjects) = rt.unsafeRun(ZIO.scoped(testCase))
      withClue(s"checking there's only an avro schema registry entry for the $avroTopic in ${subjects.mkString("\n")} (and not the json or primitive topics)") {
        subjects should contain(s"$avroTopic-value")
        subjects should not contain s"$numTopic-value"
        subjects should not contain s"$jsonTopic-value"
      }
      val values = read.map(_.record.value)
      values.size shouldBe expected
      withClue(values.mkString("\n")) {
        // we published a primitive number 2
        val numbers = values.map(_.asInt(-1)).collect {
          case x if x >= 0 => x
        }
        numbers should contain only 2
      }
    }
  }
}
