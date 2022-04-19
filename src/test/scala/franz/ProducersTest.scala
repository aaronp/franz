package franz

import io.circe.Json
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.wordspec.AnyWordSpec
import zio.{Scope, ZIO}

class ProducersTest extends BaseFranzTest {

  import SchemaGen.*

  val ids = eie.io.AlphaCounter.from(System.currentTimeMillis())


  "Producers" should {
    "demo" in {
      val p = Producers()
      import p._

      // ints and longs (for keys and values) are supported
      publish(1, 2L, "numbers")

      // and keys..
      publish(1, "text", "strings")

      // but not booleans...
      publish(1, false, "error")

      // and .... generic json
      publish("some key",
        """{
             "this" : "is",
             "json" : true
            }""".parseAsJson, "jason")

      // or ... generic (dynamic) avro (yay!)
      publish("some key",
        """{
                  "this" : "is",
                  "avro" : true
                }""".parseAsAvro("this.is.the.namespace"), "avro-topic")

      // or your own data type (so long as we can derive an encoder -- which we can do for case classes)
      case class ExampleData(someField : Int, name : String)
      publish("key", ExampleData(2, "data").asAvro("another.namespace"), "data-topic")
    }

    "be able to publish values to different topics" in {
      val p = Producers()
      val jason = Json.obj("key" -> "value".asJson)


      val avroTopic = s"producers-test-${ids.next()}"
      val avroTopic2 = s"producers-test2-${ids.next()}"
      val numTopic = s"topic-numbers-${ids.next()}"
      val jsonTopic = s"topic-json-${ids.next()}"

      val test: ZIO[Scope, Throwable, List[RecordMetadata]] = for {
        r1 <- p.publish(1, 2L, numTopic)
        r2 <- p.publish("text",
             """{
                  "this" : "is",
                  "json" : true
                }""".parseAsJson, jsonTopic)
        r5 <- p.publish("text",
          """{
                  "this" : "is",
                  "avro" : true
                }""".parseAsAvro("this.is.avro"), "avro-topic")
        r3 <- p.publish(2L, jason.asAvro("avro.test"), avroTopic)
        r4 <- p.publish("key", ExampleData(2, "data").asAvro("another.test"), avroTopic2)
      } yield List(r1, r2, r3, r4, r5)

      val results : List[RecordMetadata] = run(ZIO.scoped(test))
      val List(r1, r2, r3, r4, r5) = results
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
