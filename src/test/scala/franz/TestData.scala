package franz

import io.circe.Json
import org.apache.avro.generic.{GenericRecord, IndexedRecord}

import scala.jdk.CollectionConverters.*

object TestData {

  def fromJson(record: Json): GenericRecord = SchemaGen.recordForJson(record)

  def asJson(record: IndexedRecord): Json = {
    val jason = record.toString
    io.circe.parser.parse(jason).toTry.get
  }
}
