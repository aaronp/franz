package franz

import zio.stream.ZSink
import zio.{Chunk, ZIO}
import SchemaGen.*

class Example extends BaseFranzTest {

  val ids = eie.io.AlphaCounter.from(System.currentTimeMillis())

  "Example" should {
    "be able to work better than kafka streams" in {

      /**
        * this test demonstrates taking one topic w/ {x: ?, y: ?} data and mapping it into another topic containing the sum of x and y:
        *
        * input-topic : [ {x : 1, y :2}, { x: 4, y : 10 } ]
        * sum-topic : [ {x : 1, y :2, sum: 3}, { x: 4, y : 10, sum : 14 } ]
        */
      val config = FranzConfig()
      import config.dynamicProducerVal.*
      val inTopic = s"input-${ids.next}"
      val sumTopic = s"sums-${ids.next}"

      val testCase = for {
        // populate the input topic:
        _ <- publishValue(
          """x : 1
            |y : 2""".stripMargin.asJson,
          inTopic)
        _ <- publishValue(
          """x : 4
            |y : 10""".stripMargin.asJson,
          inTopic)
        // map that topic into a new one
        inStream <- config.batchedStream
        _ <- inStream.withTopic(inTopic).foreachRecord { r =>
          // to me, this isn't too bad for a DSL:
          val record = r.value() // get the record's value

          // treat 'x' and 'y' as integers:
          val sum = record.x.asInt() + record.y.asInt()

          // create a new record w/ the new 'sum' field
          val newRecord = record + (s"sum : ${sum}".asJson)

          // and squirt it into our sumTopic:
          publishValue(newRecord.asAvro("sum"), sumTopic)
        }.runDrain.fork

        // now, for our test, read two records from the 'sum' topic
        sumStream <- config.batchedStream
        chunk <- sumStream.withTopic(sumTopic).kafkaStream.run(ZSink.take(2))

        // some housekeeping (delete our topics)
        admin <- config.admin
        _ <- admin.deleteTopic(inTopic)
        _ <- admin.deleteTopic(sumTopic)
      } yield chunk.toList.map(_.record.value())

      val records = rt.unsafeRun(ZIO.scoped(testCase))

      records.foreach(println)
      val List(a, b) = records
      a.sum.asInt() shouldBe 3
      b.sum.asInt() shouldBe 14
    }
    "be able to aggregate a topic" in {}
    "be able to merge two streams" in {}
    "be able to read from earliest" in {}
    "be able to read from latest" in {}
    "be able to read from an offset" in {}
    "be able to read from a timestamp" in {}
  }
}
