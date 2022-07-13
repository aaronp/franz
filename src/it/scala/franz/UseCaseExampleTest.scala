package franz

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.{Logger, LoggerFactory}
import zio.*
import zio.kafka.admin.AdminClient
import zio.stream.{ZSink, ZStream}

import java.lang.System.currentTimeMillis as now

class UseCaseExampleTest extends BaseFranzTest {

  "MirrorMaker" should {
    "be able to easily produce (with custom data and types if need be) from a source topic" in {
      val from = s"some-topic-$now"
      val to = s"to-topic-$now"

      def testData: Seq[Json] =
        """person : {
              name : test
              age : 0
              address : {
                street : main
                postCode : main
                county : xyz
                country : xyz
              }
            }""".parseAsJson.asTestData(Seed(1)).take(10).toSeq

      object mirrorMaker {
        // we also 'enrich' the mapped values - we wrap them in this 'Enriched' type which stamps the source data
        case class Enriched(sourceOffset: Long, sourcePartition: Long, sourceValue: DynamicJson)

        def copy(fromTopic: String, toTopic: String): ZIO[Scope with DynamicProducer with BatchedStream, Nothing, Fiber.Runtime[Throwable, Unit]] = {
          Recipes.copyTopic(fromTopic) { record =>
            // NOTE: the destination can use any key/value types it wants (go from json to avro, long to json, whatever)
            record //
              .withKey(_.asString) // let's have avro keys in the new topic ...
              .withValue(v => Enriched(record.offset(), record.partition(), v).asAvro("new.namespace")) // ... and avro values
              .asProducerRecord(toTopic)
          }
        }
      }

      val job = for {
        _ <- Recipes.writeToTopic(from, testData)
        readBackFiber <- Recipes.takeLatestFromTopic(Set(to), 6).fork
        _ <- mirrorMaker.copy(from, to)
        readBack <- readBackFiber.join
      } yield readBack

      val results = job.run()
      val actualValues = results.map(_.value)
      val expected =
        """{"sourceOffset":0,"sourcePartition":0,"sourceValue":{"person":{"name":"4UjrHlPCWUw","age":294868933,"address":{"street":"7jAo5m3ya3M","postCode":"AmWLCF3ojf1","county":"9IhMwLPJJ5Z","country":"2"}}}}
          |{"sourceOffset":1,"sourcePartition":0,"sourceValue":{"person":{"name":"4UjrHlPCWUw","age":294868933,"address":{"street":"7jAo5m3ya3M","postCode":"AmWLCF3ojf1","county":"9IhMwLPJJ5Z","country":"2"}}}}
          |{"sourceOffset":2,"sourcePartition":0,"sourceValue":{"person":{"name":"5aRI7u2Ddxg","age":-26979293,"address":{"street":"3WOwbuvONWk","postCode":"1R928mJsSFu","county":"9nqLIbzxSZQ","country":"Aydwc6vw7G3"}}}}
          |{"sourceOffset":3,"sourcePartition":0,"sourceValue":{"person":{"name":"9KVUx9fuIKz","age":1783785617,"address":{"street":"5LvHiB6cWmR","postCode":"6y2KAyAmDJQ","county":"4klmIl0SZjq","country":"ACc6qWMGt4z"}}}}
          |{"sourceOffset":4,"sourcePartition":0,"sourceValue":{"person":{"name":"9tXiisKTqTU","age":-966230897,"address":{"street":"2wl7g3yChry","postCode":"4Sw01azhgd4","county":"8IEMsBrlIWW","country":"9P1wEZi6elX"}}}}
          |{"sourceOffset":5,"sourcePartition":0,"sourceValue":{"person":{"name":"m1ZxTUsDry","age":463535005,"address":{"street":"917Jbv3l7cG","postCode":"4eVTfMHiOiJ","county":"8VcZoDccuph","country":"8JcuvlAajfZ"}}}}"""
          .stripMargin
          .linesIterator.map(line => line.parseAsJson.asDynamicJson).toList

      withClue(actualValues.mkString("\n")) {
        actualValues should contain theSameElementsAs (expected)
      }
    }
  }

  "Basic operations" should {

    /**
      * this test demonstrates taking one topic w/ {x: ?, y: ?} data and mapping it into another topic containing the sum of x and y:
      *
      * input-topic : [ {x : 1, y :2}, { x: 4, y : 10 } ]
      * sum-topic : [ {x : 1, y :2, sum: 3}, { x: 4, y : 10, sum : 14 } ]
      */
    "be able to easily publish supported data types to any topic in any target format (avro, json, etc) w/o messing w/ SerDe types" in {

      // easily create json data with '.parseAsJson'
      val jsonData =
        """x : 1
            y : 2
            array : [a,b,c]
            nested :  {
              what : ever
            }""".parseAsJson

      // easily turn json into an avro record with 'asAvro' by inferring the schema
      val avroData: GenericRecord = jsonData.asAvro("my.namespace")

      // or just use regular case classes
      case class MoreData(id: Long, ok: Boolean)
      case class MyData(some: Int, value: MoreData)

      val objectData = MyData(1, MoreData(2, false))

      // program which reads enriches a stream and sums the 'x' and 'y' components
      val sum = for {
        writer <- ZIO.service[DynamicProducer]
        reader <- ZIO.service[BatchedStream]
        _ <- reader.withTopics("avro-topic-1").foreachRecord { r =>

          // treat 'x' and 'y' as integers:
          val sum = r.value.x.asInt() + r.value.y.asInt()

          // create a new record w/ the new 'sum' field
          val newRecord = r.value() + s"sum : $sum".parseAsJson

          // and squirt it into our sumTopic:
          writer.publishValue(newRecord.asAvro("sum"), "enriched-topic")
        }
      } yield ()


      val myApp: ZIO[Scope with BatchedStream with DynamicProducer, Throwable, List[DynamicJson]] = for {
        writer <- ZIO.service[DynamicProducer]
        readBackFiber <- Recipes.takeLatestFromTopic(Set("json-topic", "another-json-topic", "int-topic", "another-json-topic", "avro-topic-1", "avro-topic-2", "enriched-topic"), 6).fork
        _ <- ZIO.sleep(zio.Duration.fromMillis(2000))
        _ <- writer.publishValue(jsonData, "json-topic")
        _ <- writer.publishValue(123, "int-topic")
        _ <- writer.publishValue(objectData.asJson, "another-json-topic")
        _ <- writer.publishValue(avroData, "avro-topic-1")
        _ <- writer.publishValue(objectData.asAvro("another.namespace"), "avro-topic-2")
        _ <- sum.fork
        readBack <- readBackFiber.join
      } yield readBack.map(_.value)

      val result: Seq[DynamicJson] = myApp.run()

      val expected = {
        val records =
          """{"array":["a","b","c"],"nested":{"what":"ever"},"x":1,"y":2}
            |{"some":1,"value":{"id":2,"ok":false}}
            |{"array":["a","b","c"],"nested":{"what":"ever"},"x":1,"y":2}
            |{"some":1,"value":{"id":2,"ok":false}}
            |{"x":1,"y":2,"array":["a","b","c"],"nested":{"what":"ever"},"sum":3}""".stripMargin.linesIterator.map(line => line.parseAsJson.asDynamicJson).toList

        Json.fromInt(123).asDynamicJson +: records
      }
      withClue(result.mkString("\n")) {
        result should contain theSameElementsAs (expected)
      }
    }
  }

  "Streaming" should {

    "be able to aggregate a topic (e.g. sum a stream of integers)" in {

      // this is some custom code -- it declares some input data (x y coords)
      // and 'enriches' it to another topic which adds 'xTotal, yTotal, total' fields
      // it does this by using a ZIO streams 'mapAccum' function and pipes the resulting
      // stream into another topic
      object runningTotalApp {
        def testData =
          """x: 1
           y: 1""".parseAsJson.asTestData(Seed(123))

        def asJson(x: Int, y: Int, xTotal: Int, yTotal: Int, total: Int): DynamicJson =
          s"""
           x: $x
           y: $y
           xTotal : $xTotal
           yTotal : $yTotal
           total : $total""".parseAsJson.asDynamicJson

        def sum(accum: DynamicJson, x: Int, y: Int): DynamicJson = {
          val xTotal = accum.xTotal.asInt()
          val yTotal = accum.yTotal.asInt()
          val total = accum.total.asInt()
          asJson(x, y, xTotal + x, yTotal + y, total + x + y)
        }

        def zero = asJson(0, 0, 0, 0, 0)
      }

      val from = s"xy-topic-$now"
      val to = s"xy-sum-$now"
      import runningTotalApp.*

      val job = for {
        inputData <- Recipes.streamEarliest(Set(from))
        pipedData <- Recipes.takeLatestFromTopic(Set(to), 10).fork
        mappedStream: ZStream[Any, Throwable, DynamicJson] = inputData.mapAccum(zero) {
          case (data, next) =>
            val x = next.value.x.asInt().abs % 10
            val y = next.value.y.asInt().abs % 10
            val runningTotal = sum(data, x, y)
            (runningTotal, runningTotal)
        }
        _ <- Recipes.writeToTopic(from, testData.take(10).to(Iterable))
        _ <- Recipes.pipeToTopic(mappedStream, to).fork
        result <- pipedData.join
      } yield result

      val expected: String =
        """{"total":7,"x":3,"xTotal":3,"y":4,"yTotal":4}
          |{"total":14,"x":3,"xTotal":6,"y":4,"yTotal":8}
          |{"total":21,"x":1,"xTotal":7,"y":6,"yTotal":14}
          |{"total":26,"x":3,"xTotal":10,"y":2,"yTotal":16}
          |{"total":35,"x":9,"xTotal":19,"y":0,"yTotal":16}
          |{"total":46,"x":7,"xTotal":26,"y":4,"yTotal":20}
          |{"total":47,"x":1,"xTotal":27,"y":0,"yTotal":20}
          |{"total":56,"x":1,"xTotal":28,"y":8,"yTotal":28}
          |{"total":57,"x":1,"xTotal":29,"y":0,"yTotal":28}
          |{"total":62,"x":5,"xTotal":34,"y":0,"yTotal":28}""".stripMargin
      val expectedRecords = expected.linesIterator.map(line => line.parseAsJson.asDynamicJson).toList
      val actualRecords: Seq[DynamicJson] = job.run().map(_.value)
      withClue(actualRecords.mkString("\n")) {
        actualRecords should contain theSameElementsAs (expectedRecords)
      }
    }

  }
}
