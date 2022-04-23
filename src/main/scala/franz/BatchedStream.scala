package franz

import codetemplate.DynamicJson
import zio.*
import zio.kafka.consumer.*
import zio.kafka.serde.Deserializer
import zio.stream.ZStream

final case class BatchedStream(topic: Subscription,
                         consumerSettings: ConsumerSettings,
                         batchSize: Int,
                         batchLimit: scala.concurrent.duration.FiniteDuration,
                         keyDeserializer: Deserializer[Any, DynamicJson],
                         valueDeserializer: Deserializer[Any, DynamicJson],
                         blockOnCommit: Boolean) {

  def withTopic(topic: String) = withTopics(topic)

  def withTopics(topic: String, theRest: String*) = copy(topic = Subscription.topics(topic, theRest: _*))

  type K = DynamicJson
  type V = DynamicJson
  lazy val kafkaStream: ZStream[Any, Throwable, CommittableRecord[K, K]] =
    Consumer
      .subscribeAnd(topic)
      .plainStream(keyDeserializer, valueDeserializer)
      .provideSomeLayer(ZLayer.scoped(Consumer.make(consumerSettings)))

  // : ZStream[ZEnv, Throwable, Chunk[CommittableRecord[K, V]]]
  lazy val batchedStream: ZStream[Any, Throwable, Chunk[CommittableRecord[K, K]]] = {
    batchLimit.toMillis match {
      case 0 => kafkaStream.grouped(batchSize) //.provideLayer(consumerLayer)
      case timeWindow => kafkaStream.groupedWithin(batchSize, Duration.fromMillis(timeWindow)) //.provideLayer(consumerLayer)
    }
  }

  val consumerLayer: ZIO[Scope, Throwable, Consumer] = Consumer.make(consumerSettings)

  def run(persist: Array[CommittableRecord[K, V]] => RIO[ZEnv, Unit]): ZStream[ZEnv, Throwable, Int] = {
    def persistBatch(batch: Chunk[CommittableRecord[K, V]]): ZIO[zio.ZEnv, Throwable, Int] = {
      val offsets = batch.map(_.offset).foldLeft(OffsetBatch.empty)(_ merge _)
      if (batch.isEmpty) {
        Task.succeed(0)
      } else {
        for {
          _ <- persist(batch.toArray)
          _ <- if (blockOnCommit) offsets.commit else offsets.commit.fork
        } yield batch.size
      }
    }

    batchedStream.mapZIO(persistBatch)
  }
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
      keys <- deserializer(true)
      values <- deserializer(false)
    } yield BatchedStream(subscription, consumerSettings, batchSize, batchWindow, keys, values, blockOnCommits)
  }
}
