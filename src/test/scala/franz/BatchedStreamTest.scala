package franz

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.consumer.CommittableRecord
import zio.stream.ZStream
import zio.{Chunk, Ref}

import java.nio.ByteBuffer
import java.util
import java.util.UUID

class BatchedStreamTest extends BaseFranzTest {

  def avroRecord(k: Int): GenericRecord = {
    import io.circe.syntax.*
    TestData.fromJson(
      Map(
        "name" -> "test".asJson,
        "id"   -> k.asJson
      ).asJson)
  }
}
