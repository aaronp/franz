package franz

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.wordspec.AnyWordSpec
import zio.{Scope, ZIO}

class ProducersTest extends BaseFranzTest {

  import SchemaGen.*

  val ids = eie.io.AlphaCounter.from(System.currentTimeMillis())

  case class ExampleData(someField: Int, name: String)

  "Producers" should {

    "be able to publish values to different topics" in {
      val config = FranzConfig()

      val jason = "key : value".parseAsJson

      val avroTopic = s"producers-test-${ids.next()}"
      val avroTopic2 = s"producers-test2-${ids.next()}"
      val numTopic = s"topic-numbers-${ids.next()}"
      val jsonTopic = s"topic-json-${ids.next()}"

      val test: ZIO[Scope, Throwable, List[RecordMetadata]] = for {
        p <- config.dynamicProducer
        r1 <- p.publish(1, 2L, numTopic)
        r2 <- p.publish("text",
          """{
                  "this" : "is",
                  "json" : true
                }""".parseAsJson,
          jsonTopic)
        r5 <- p.publish("text",
          """{
                  "this" : "is",
                  "avro" : true
                }""".parseAsAvro("this.is.avro"),
          "avro-topic")
        r3 <- p.publish(2L, jason.asAvro("avro.test"), avroTopic)
        r4 <- p.publish("key", ExampleData(2, "data").asAvro("another.test"), avroTopic2)
        _ <- config.admin.flatMap { admin =>
          for {
            _ <- admin.deleteTopic(avroTopic)
            _ <- admin.deleteTopic(avroTopic2)
            _ <- admin.deleteTopic(numTopic)
            _ <- admin.deleteTopic(jsonTopic)
          } yield ()
        }
      } yield List(r1, r2, r3, r4, r5)

      val results: List[RecordMetadata] = run(ZIO.scoped(test))
      val List(r1, r2, r3, _, _) = results
      r1.offset() shouldBe 0
      r1.partition() shouldBe 0
      r1.topic() shouldBe numTopic

      r2.offset() shouldBe 0
      r2.partition() shouldBe 0
      r2.topic() shouldBe jsonTopic

      r3.offset() shouldBe 0
      r3.partition() shouldBe 0
      r3.topic() shouldBe avroTopic
    }
  }
}
