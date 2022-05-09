package franz

import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.kafka.admin.AdminClient
import zio.stream.{ZSink, ZStream}
import zio.{Chunk, Fiber, Scope, ZIO}

/**
  * Various recipes for operating on Kafka
  */
object Recipes {

  def offsetsForTopic(topic: String) = {
    for {
      admin <- ZIO.service[AdminClient]
      countByTopic <- admin.countByTopic(Set(topic))
    } yield countByTopic(topic)
  }

  def writeToTopic(topic: String, values: Iterable[Supported]): ZIO[Scope with DynamicProducer, Throwable, Unit] = for {
    writer <- ZIO.service[DynamicProducer]
    _ <- ZIO.foreach(values) { jsonData =>
      writer.publishValue(jsonData, topic)
    }
  } yield ()

  def pipeToTopic(values: ZStream[Any, Throwable, Supported], topic: String | Null = null): ZIO[Scope with DynamicProducer, Throwable, Unit] = for {
    writer <- ZIO.service[DynamicProducer]
    _ <- values.run(
      ZSink.foldLeftChunksZIO[Scope, Throwable, Supported, Int](0) {
        case (x, chunk) => writer.publishRecordValues(chunk, topic).as(x)
      }
    )
  } yield ()

  /**
    * Writes the stream to kafka given the 'asKey' function for creating the kafka keys
    * @param values
    * @param asKey
    * @tparam K
    * @tparam V
    * @return
    */
  def pipeToTopicWithKeys[K <: Supported, V <: Supported](values: ZStream[Any, Throwable, V])(asKey: V => K): ZIO[Scope with DynamicProducer, Throwable, Unit] = for {
    writer <- ZIO.service[DynamicProducer]
    _ <- values.run(
      ZSink.foldLeftChunksZIO[Scope, Throwable, V, Chunk[RecordMetadata]](Chunk.empty[RecordMetadata]) {
        case (_, chunk) =>
          val p: ZIO[Scope, Throwable, Chunk[RecordMetadata]] = writer.publishRecordValuesAndKeys[K, V](chunk, asKey)
          p
      }
    )
  } yield ()

  /**
    * a 'mirror-maker' example
    *
    * @param fromTopic the source topic
    * @param mapRecord a function which converts the input records to producer records
    * @tparam K
    * @tparam V
    * @return a job which copies a kafka topic
    */
  def copyTopic[K <: Supported, V <: Supported](fromTopic: String)(mapRecord: KafkaRecord => ProducerRecord[K, V] = _.asProducerRecord()): ZIO[Scope with DynamicProducer with BatchedStream, Nothing, Fiber.Runtime[Throwable, Unit]] = {
    for {
      reader <- ZIO.service[BatchedStream]
      writer <- ZIO.service[DynamicProducer]
      fiber <- reader.withTopic(fromTopic).onBatch { batch =>
        writer.publishRecords(batch.map(mapRecord)).unit
      }.runDrain.fork
    } yield fiber
  }

  def takeLatestFromTopic(topics: Set[String], limit: Int): ZIO[BatchedStream & Scope, Throwable, List[KafkaRecord]] = {
    for {
      list <- takeLatestFromTopicEither(topics, limit)
      mapped <- ZIO.foreach(list)(ZIO.fromEither)
    } yield mapped.map(_.record)
  }

  def takeLatestFromTopicEither(topics: Set[String], limit: Int): ZIO[BatchedStream & Scope, Throwable, List[Either[Throwable, CRecord]]] = {
    streamLatest(topics).flatMap(_.take(limit).toIterator.map(_.take(limit).toList))
  }

  def streamLatest(topics: Set[String]): ZIO[BatchedStream, Nothing, ZStream[Any, Throwable, CRecord]] = for {
    original <- ZIO.service[BatchedStream]
    reader = original.withTopics(topics).withGroupId().withConsumerOffsetLatest
  } yield reader.kafkaStream

  def streamEarliest(topics: Set[String]): ZIO[BatchedStream, Nothing, ZStream[Any, Throwable, CRecord]] = for {
    original <- ZIO.service[BatchedStream]
    reader = original.withTopics(topics).withGroupId().withConsumerOffsetEarliest
  } yield reader.kafkaStream
}
