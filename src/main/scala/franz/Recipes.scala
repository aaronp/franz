package franz

import franz.DynamicProducer.Supported
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.admin.AdminClient
import zio.{Fiber, Scope, ZIO}

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

  def writeToTopic(topic: String, values: Seq[DynamicProducer.Supported]): ZIO[Scope with DynamicProducer, Throwable, Unit] = for {
    writer <- ZIO.service[DynamicProducer]
    _ <- ZIO.foreach(values) { jsonData =>
      writer.publishValue(jsonData, topic)
    }
  } yield ()

  /**
    * a 'mirror-maker' example
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

  def takeFromTopic(topics: Set[String], limit: Int): ZIO[BatchedStream & Scope, Throwable, List[KafkaRecord]] = {
    for {
      list <- takeFromTopicEither(topics, limit)
      mapped <- ZIO.foreach(list)(ZIO.fromEither)
    } yield mapped.map(_.record)
  }

  def takeFromTopicEither(topics: Set[String], limit: Int): ZIO[BatchedStream & Scope, Throwable, List[Either[Throwable, CRecord]]] = {
    for {
      original <- ZIO.service[BatchedStream]
      reader = original.withTopics(topics).withGroupId().withConsumerOffsetLatest
      iter <- reader.kafkaStream.take(limit).toIterator
    } yield iter.toList
  }
}
