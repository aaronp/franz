package franz

import io.circe.{Encoder, Json}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type.*

import scala.collection.mutable
import scala.util.Try

/**
  * Code which knows how to create test json from an avro schema
  */
object DataGen {

  /**
    * @return a list of json values representing some test data in the (json) shape of 'A'
    */
  def repeatFromTemplate[A: Encoder](value: A, num: Int, initialSeed: Seed = Seed()): Iterator[Json] =
    repeatFromTemplateIter(value, initialSeed).take(num)

  def repeatFromTemplateIter[A: Encoder](value: A, initialSeed: Seed = Seed()): Iterator[Json] = {
    val jason = Encoder[A].apply(value)
    val schema = SchemaGen(jason)
    val (s1, first) = recordForSchema(schema, initialSeed)
    val tail = Iterator.from(0).scanLeft((s1, first)) {
      case ((seed, _), _) => recordForSchema(schema, seed)
    }
    Iterator(first) ++ tail.map(_._2)
  }

  def apply[A: Encoder](value: A, seed: Seed = Seed()): Json = {
    val jason = Encoder[A].apply(value)
    val schema = SchemaGen(jason)
    recordForSchema(schema, seed)._2
  }

  import scala.jdk.CollectionConverters.{*, given}

  def parseAvro(avro: String): Try[Schema] = {
    val parser = new org.apache.avro.Schema.Parser
    Try(parser.parse(avro))
  }

  extension (x: Long) {
    def isOdd = x % 2 == 0
  }

  case class Gen(seed: Long) {
    def string: String = eie.io.AlphaCounter.from(seed).next()

    def long: Long = seed

    def int: Int = seed.toInt

    def float: Float = seed.toFloat

    def double: Double = seed.toDouble

    def bool: Boolean = (seed % 7).isOdd
  }

  object Gen {
    def forSeed(seed: Seed) = seed.next -> Gen(seed.long)
  }

  extension[X, Y] (pear: (X, Y)) {
    def map[A](f: Y => A): (X, A) = (pear._1, f(pear._2))
  }

  def forSchema(schema: Schema, seed: Long = System.currentTimeMillis()): Json = recordForSchema(schema, Seed(seed))._2

  /**
    *
    * @param schema the avro schema
    * @param seed   the initial seed used for 'random' values
    * @param gen    the generator (state monad for FP randomness (so we can have FP 'random' values - e.g. they're consistent))
    * @return the seed (which can be ignored/dropped) and some test data
    */
  def recordForSchema(schema: Schema, seed: Seed = Seed(), gen: Seed => (Seed, Gen) = Gen.forSeed): (Seed, Json) = {
    schema.getType match {
      case RECORD =>
        val (s, pears) = schema.getFields.asScala.foldLeft(seed -> List[(String, Json)]()) {
          case ((s, fields), field) =>
            val (next, json) = recordForSchema(field.schema(), s, gen)
            (next, (field.name(), json) :: fields)
        }
        s -> Json.obj(pears.toArray *)
      case ENUM =>
        val symbols = schema.getEnumSymbols
        val size = symbols.size()
        val index = seed.int(size - 1)
        val s = symbols.get(index)
        seed.next -> Json.fromString(s)
      case ARRAY =>
        val (newSeed1, a) = recordForSchema(schema.getElementType, seed, gen)
        val (newSeed2, b) = recordForSchema(schema.getElementType, newSeed1, gen)
        newSeed2 -> Json.arr(a, b)
      case MAP =>
        val (s1, name1) = gen(seed)
        val (s2, name2) = gen(s1)
        val (s3, a) = recordForSchema(schema.getValueType, s2, gen)
        val (s4, b) = recordForSchema(schema.getValueType, s3, gen)
        s4 -> Json.obj(name1.string -> a, name2.string -> b)
      case UNION =>
        val nonNull = schema.getTypes.asScala.filterNot(_.isNullable).headOption
        nonNull.fold(seed -> Json.Null)(recordForSchema(_, seed, gen))
      case FIXED => gen(seed).map(g => Json.fromInt(g.int))
      case STRING => gen(seed).map(g => Json.fromString(g.string))
      case BYTES => gen(seed).map(g => Json.arr(Json.fromInt(g.int)))
      case INT => gen(seed).map(g => Json.fromInt(g.int))
      case LONG => gen(seed).map((g: Gen) => Json.fromLong(g.long))
      case FLOAT => gen(seed).map((g: Gen) => Json.fromFloat(g.float).getOrElse(Json.fromLong(g.long)))
      case DOUBLE => gen(seed).map((g: Gen) => Json.fromDouble(g.double).getOrElse(Json.fromLong(g.long)))
      case BOOLEAN => gen(seed).map((g: Gen) => Json.fromBoolean(g.bool))
      case NULL => (seed.next, Json.Null)
    }
  }
}
