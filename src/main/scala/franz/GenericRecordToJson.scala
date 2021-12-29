package franz

import io.circe.Json
import org.apache.avro.generic.{GenericData, GenericRecord, IndexedRecord}

object GenericRecordToJson {
  def apply(record: IndexedRecord): Json = {
    val jason = GenericData.get.toString(record)
    io.circe.parser.parse(jason).toTry.get
  }
}
