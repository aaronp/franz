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

class BatchedStreamTest extends BaseFranzTest {

  val ids = eie.io.AlphaCounter.from(System.currentTimeMillis())

  case class Child(value: String, truthy: Boolean)

  case class Parent(name: String, id: Int, kid: Child)

  "BatchedStream" should {
    "be able to read avro data" in {
      val data = DataGen(Parent("", 0, Child("", true)))
      val p = Producers()

      val avroTopic = s"BatchedStreamTest-${ids.next()}"

      import SchemaGen.*

      // this test publishes a bunch of different types to different topics, then reads 'em
      val test = for {
        _ <- p.publish("key", data.asAvro("this.is.avro"), avroTopic)
        reader <- BatchedStream()
        head <- reader.withTopic(avroTopic).kafkaStream.runHead
      } yield head

      //      val q = test.taskValue()
      //      val Some(committableRecord : CommittableRecord[DynamicJson, DynamicJson]) = rt.unsafeRun(ZIO.scoped(test))
      val Some(committableRecord: CommittableRecord[DynamicJson, DynamicJson]) = rt.unsafeRun(ZIO.scoped(test))

      val key = committableRecord.key
      val value = committableRecord.value
      println(committableRecord.value)
      println(key)
      println(key.getClass)
      println(value)
      println(value.getClass)
      println(value)
    }

    "be able to read from any topic" ignore {
      val data = DataGen.repeatFromTemplate(Parent("", 0, Child("", true)), 10)
      val expected = 21 // two lots of 10 records, as well as a single numeric record

      val p = Producers()

      val avroTopic = s"producers-test-${ids.next()}"
      val numTopic = s"topic-numbers-${ids.next()}"
      val jsonTopic = s"topic-json-${ids.next()}"

      import SchemaGen.*

      // this test publishes a bunch of different types to different topics, then reads 'em
      val test: ZIO[Scope, Throwable, List[Take[Throwable, CommittableRecord[BatchedStream#K, BatchedStream#K]]]] = for {
        _ <- p.publish(1, 2L, numTopic)
        _ <- ZIO.foreach(data) { d =>
          p.publish(d, d.asAvro("this.is.avro"), avroTopic)
        }
        _ <- ZIO.foreach(data) { d =>
          p.publish("text", d, jsonTopic)
        }
        reader <- BatchedStream()
        queue <- reader.withTopics(avroTopic, numTopic, jsonTopic).kafkaStream.take(expected).toQueue()
        list <- queue.takeUpTo(expected)
      } yield list.toList

      val y: Task[List[Take[Throwable, CommittableRecord[BatchedStream#K, BatchedStream#K]]]] = ZIO.scoped(test)
      val x = rt.unsafeRun(y)
    }

  }

}
