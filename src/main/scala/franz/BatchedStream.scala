package franz

import org.slf4j.LoggerFactory
import zio.*
import zio.kafka.consumer.*
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.serde.Deserializer
import zio.stream.ZStream

import java.util.UUID

final case class BatchedStream(topic: Subscription,
                               consumerSettings: ConsumerSettings,
                               batchSize: Int,
                               batchLimit: scala.concurrent.duration.FiniteDuration,
                               keyDeserializer: Deserializer[Any, DynamicJson],
                               valueDeserializer: Deserializer[Any, DynamicJson],
                               blockOnCommit: Boolean) {

  /**
    * A convenience method to execute some 'persist' thunk which, on success, will kermit the offsets back to kafka
    *
    * @param onBatchThunk the job to run on each batch
    * @return a stream of batch sizes
    */
  def onBatch(onBatchThunk: Array[KafkaRecord] => RIO[ZEnv, Unit]): ZStream[ZEnv, Throwable, Int] = {
    def persistBatch(batch: Chunk[CRecord]): ZIO[zio.ZEnv, Throwable, Int] = {
      val offsets = batch.map(_.offset).foldLeft(OffsetBatch.empty)(_ merge _)
      if (batch.isEmpty) {
        Task.succeed(0)
      } else {
        for {
          _ <- onBatchThunk(batch.map(_.record).toArray)
          _ <- if (blockOnCommit) offsets.commit else offsets.commit.fork
        } yield batch.size
      }
    }

    batchedStream.mapZIO(persistBatch)
  }

  /**
    * A convenience method to execute (and commit) a thunk on each record
    *
    * @param onBatchThunk the job to run on each batch
    * @return a stream of batch sizes
    */
  def foreachRecord[R, E1 >: Throwable, A](thunk: KafkaRecord => ZIO[R, E1, A]): ZIO[R, E1, Unit] = {
    def onRecord(record: CRecord) = {
      for {
        res <- thunk(record.record)
        _   <- if (blockOnCommit) record.offset.commit else record.offset.commit.fork
      } yield res
    }

    kafkaStream.mapZIO(onRecord).runDrain
  }

  private def random() = java.util.UUID.randomUUID().toString

  def withTopic(topic: String): BatchedStream = withTopics(topic)

  def fromEarliest(groupId: String = random()) = withOffsetReset("earliest", groupId)

  def fromLatest(groupId: String = random()) = withOffsetReset("latest", groupId)

  def fromOffset(offset: Int, groupId: String = random()) = withOffsetReset(offset.toString, groupId)

  def withOffsetReset(name: String, groupId: String = random()) =
    copy(consumerSettings = consumerSettings.withProperty("auto.offset.reset", name).withGroupId(groupId))

  def withTopics(topic: String, theRest: String*) = copy(topic = Subscription.topics(topic, theRest: _*))

  def withTopics(topics : Set[String]) = copy(topic = Subscription.Topics(topics))

  def withGroupId(groupId : String = UUID.randomUUID().toString) = withConsumerSettings(_.withGroupId(groupId))

  def withConsumerSettings(f : ConsumerSettings => ConsumerSettings) = copy(consumerSettings = f(consumerSettings))
  def withConsumerOffsetLatest = withConsumerOffset(AutoOffsetStrategy.Latest)
  def withConsumerOffsetEarliest = withConsumerOffset(AutoOffsetStrategy.Earliest)

  def withConsumerOffset(reset: AutoOffsetStrategy = AutoOffsetStrategy.Latest) = withConsumerSettings { c =>
    c.withOffsetRetrieval(OffsetRetrieval.Auto(reset))
  }

  lazy val kafkaStream: ZStream[Any, Throwable, CRecord] =
    Consumer
      .subscribeAnd(topic)
      .plainStream(keyDeserializer, valueDeserializer)
      .provideSomeLayer(ZLayer.scoped(Consumer.make(consumerSettings)))

  lazy val batchedStream: ZStream[Any, Throwable, Chunk[CRecord]] = {
    batchLimit.toMillis match {
      case 0          => kafkaStream.grouped(batchSize)
      case timeWindow => kafkaStream.groupedWithin(batchSize, Duration.fromMillis(timeWindow))
    }
  }

  val consumerLayer: ZIO[Scope, Throwable, Consumer] = Consumer.make(consumerSettings)

}

/** A Kafka stream which will batch up records by the least of either a time-window or max-size,
  * and then use the provided 'persist' function on each batch
  */
object BatchedStream {
  type JsonString = String

  /** @param config our parsed typesafe config
    * @return a managed resource which will return the running stream
    */
  def apply(config: FranzConfig = FranzConfig()): Task[BatchedStream] = {
    import config.*
    for {
      keys   <- deserializer(true)
      values <- deserializer(false)
    } yield BatchedStream(subscription, consumerSettings, batchSize, batchWindow, keys, values, blockOnCommits)
  }
}
