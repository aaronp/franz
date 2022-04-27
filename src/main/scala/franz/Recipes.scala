package franz

import zio.{Scope, ZIO}
import zio.kafka.admin.AdminClient

/**
  * Various recipes for operating on Kafka
  */
object Recipes {

  def offsetsForTopic(topic: String) = {
    for {
      admin        <- ZIO.service[AdminClient]
      countByTopic <- admin.countByTopic(Set(topic))
    } yield countByTopic(topic)
  }

  def takeFromTopic(topics: Set[String], limit: Int): ZIO[BatchedStream & Scope, Throwable, List[KafkaRecord]] = {
    for {
      list   <- takeFromTopicEither(topics, limit)
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
