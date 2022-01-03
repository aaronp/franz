package franz

import zio.*
import zio.duration.Duration
import zio.kafka.consumer.*
import zio.kafka.serde.Deserializer
import zio.stream.ZStream

case class BatchedStream[K, V](topic: Subscription,
                               consumerSettings: ConsumerSettings,
                               batchSize: Int,
                               batchLimit: scala.concurrent.duration.FiniteDuration,
                               keyDeserializer: Deserializer[Any, K],
                               valueDeserializer: Deserializer[Any, V],
                               blockOnCommit: Boolean) {

  lazy val kafkaStream = Consumer.subscribeAnd(topic).plainStream(keyDeserializer, valueDeserializer)

  lazy val batchedStream: ZStream[ZEnv, Throwable, Chunk[CommittableRecord[K, V]]] = {
    batchLimit.toMillis match {
      case 0          => kafkaStream.grouped(batchSize).provideCustomLayer(consumerLayer)
      case timeWindow => kafkaStream.groupedWithin(batchSize, Duration.fromMillis(timeWindow)).provideCustomLayer(consumerLayer)
    }
  }

  val consumerLayer = ZLayer.fromManaged(Consumer.make(consumerSettings))

  def run(persist: Array[CommittableRecord[_, _]] => RIO[ZEnv, Unit]): ZStream[zio.ZEnv, Throwable, Int] = {
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

    batchedStream.mapM(persistBatch)
  }
}

/** A Kafka stream which will batch up records by the least of either a time-window or max-size,
  * and then use the provided 'persist' function on each batch
  */
object BatchedStream {
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  type JsonString = String

  /** @param config our parsed typesafe config
    * @return a managed resource which will return the running stream
    */
  def apply[K, V](config: FranzConfig = FranzConfig()): Task[BatchedStream[K, V]] = {
    import config.*
    for {
      keys   <- consumerKeySerde[K]
      values <- consumerValueSerde[V]
    } yield BatchedStream(subscription, consumerSettings, batchSize, batchWindow, keys, values, blockOnCommits)

  }
}
