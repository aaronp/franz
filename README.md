# Franz 
![https://github.com/aaronp/franz/actions](https://github.com/aaronp/franz/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/franz_3/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/franz_3)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Purpose

This library exists to create a better developer experience "DevEx" when working with kafka.

The ultimate goal is to allow very quick (and yet resource-safe, testable, readable, clean) development of streams which is NOT currently the case.

Problems:
 * There's a disparate offering of Kafka Connect, Kafka Streams/KSQL:
   * Kafka Connect should be *just* simple source/sink adapters
   * Kafka Streams/KSQL is all about in and out of Kafka -- and comes with additional serialisation middleware/baggage/plumming (e.g. Serde's)

## Target
The aim is to improve on the example given by [47deg here](https://www.47deg.com/blog/run-fs2-kafka-scala-cli/).

That is, can we treat scala like a dynamically-typed/scripted language and avoid the (1) build a library/image (2) update devops pipelines w/ that version.

And instead just have a terse, single-screen business logic script so that we can throw away step #1 and make the whole code fit into a ConfigMap by leveraging e.g. [scala-cli](https://scala-cli.virtuslab.org/)?

## Goals

1. Simplify (by completely removing) all the Serde stuff
   * remove the generic 'Key' and 'Value' type signatures
   * infer/provide the right Serde based on the actual types we use

*How*: By just using a better data type which can be structured but also represent all types (strings, longs, ints, avro, json, protobuf)

TL;DR: A "DynamicJson" type which can easily marshal into/out of case-classes, but provide dynamic 'record.value.a.b.c.asInt' semantics

2. Still provide a performant, resource-safe way to work with streams with concurrency/parallelism in mind

*How*: By using [ZIO](https://zio.dev/next/overview/)
TL;DR: A bit like "Future[A]" think "IO[A]" where 'IO' can be rerun/restarted, raced, fork/joined/cancelled, and a bunch of other stuff.

3. Provide this as a library, so [we can completely remove the separate CI/CD overheads](https://www.47deg.com/blog/run-fs2-kafka-scala-cli/)

Because we have powerful constructs for our code:
 * mapping/filtering/flat-mapping with common data types (e.g. avro, protobuf, schema registry)
 * going into/out-of sinks (kafka, endpoints, databases, file-systems, stdout)

We can create great tooling which interops w/ databases, REST services, etc in a few lines of code

## Examples

For these examples, assume these imports and data types
```scala
import franz.*
import io.circe.generic.auto.*
import io.circe.syntax.*

case class MoreData(id: Long, ok: Boolean)
case class MyData(some: Int, value: MoreData)

val objectData = MyData(1, MoreData(2, false))
```

### Provides convenience methods (parseAsJson) for create json types from JSON or HOCON

```scala
   val jsonData : Json =
     """x : 1
        y : 2
        array : [a,b,c]
        nested :  {
           what : ever
        }""".parseAsJson
```

### Generate test data using `DataGen`

Useful for populating topics from some template examples.

This will repeat random (though consistent using purely functional Random)
```scala
val testData: Seq[Json] = DataGen.repeatFromTemplate(objectData, 10)

// produces values such as:
//{
//   "value" : {
//      "ok" : true,
//      "id" : 1655878085
//   },
//   "some" : -606295026
//}
```

Where that template data could also just be some json:

```scala
val testRecord = DataGen("""bool : false
            y : 2
            array : [a,b,c]
            nested :  {
              what : ever
            }""".parseAsJson)

// where testRecord would then be:
//{
//   "y" : -942827898,
//   "nested" : {
//      "what" : "4SiSlSbwvWG"
//   },
//   "bool" : true,
//   "array" : [
//     "T49HQtn",
//     "VhtGR7TV62"
//   ]
//}
```  

### Working with Avro

The `SchemaGen` class can derive schemas like [avro4s](https://github.com/sksamuel/avro4s)

Where the `.asAvro("your.namespace.here")` produces avro records from case classes (products) or json:
```scala
val record : IndexedRecord = objectData.asAvro("some.namespace")
val fromJsonRecord : IndexedRecord = """bool : false
            y : 2
            array : [a,b,c]
            nested :  {
              what : ever
            }""".parseAsAvro("another.namespace")
```

### Better Kafka "DevEx" (developer experience)

One main issue w/ Kafka is specifying all the Serde (serializers / deserializers), which makes it 
difficult to simply map data into/out-of Kafka.

Franz provides a `DynamicProducer` which can read and write any supported type (primitives, json, avro) to Kafka,
making working with the data (mapping, filtering, flatMapping) much easier:

```scala
// we use the same writer to write different data to different topics:
val writer : DynamicProducer = ... 
writer.publishValue(jsonData, "json-topic")
writer.publishValue(123, "int-topic")
writer.publishValue(objectData.asJson, "another-json-topic")
writer.publishValue(objectData.asAvro("another.namespace"), "avro-topic-2")
writer.publishValue(avroData, "avro-topic-1")
```

Where unsupported values to 'publishValue' won't compile. This is thanks to Scala 3's [union type](https://docs.scala-lang.org/scala3/book/types-union.html), and `SupportedType` being defined as:
```scala
  type Supported = Int | Long | DynamicJson | Json | IndexedRecord | String | ByteBuffer
```

### Reading from Kafka using DynamicJson

Reading works in a similar simple way - Franz provides a `BatchedStream` which can read *any* supported type from Kafka.

The Key and Value types are always of `DynamicJson` (as avro, json, primitives, etc can all be represented as json).

The Franz 'DynamicJson' type (rather than just Json directly) provides some additional functionality:

#### Dynamic field access

consider this json:
```
val json : DynamicJson = {
  person : {
    address : {
      street : "main steet"
      ...
    }
  }
}

// we can just use dot notation of 'person.address.street' (and then 'asString' for the expected type)
val street : String = json.person.address.street.asString
```

### Putting it all together

Given the excellent properties of [ZIO](https://zio.dev/next/overview/): 
 * composability
 * resource safety
 * concurrency support (fibers, fork/join, racing)
 * rich functionality (retries, fallback/recovery, delays, scheduling...) 

Mixed with the utilities offered by Franz:
 * Json utilities (uses [Circe](https://circe.github.io/circe/)) 
 * SchemaGen for deriving avro schemas from classes or json
 * DataGen for generating random (but consistently/reproducibly so) test data
 * BatchedStream for convenience read operations and automatic key/value SerDe
 * DynamicProducer for convenient write operations (and derived Serde based on values)

Let's consider an example where we write some json data (with x and y values) to a topic,
and then enrich that data by adding a 'sum' field written as avro to another topic.

Let's write a single record to `in-topic`:

```scala
val writeOneRecord = for {
  writer <- ZIO.service[DynamicProducer]
  _ <- writer.publishValue(
    """
      x : 1
      y : 2
      """.parseAsJson, "in-topic")
} yield ()
```

or many records using `DataGen`:

```scala
 val writeManyRecords = for {
  writer <- ZIO.service[DynamicProducer]
  testData = DataGen.repeatFromTemplate("""
      x : 1
      y : 2
      """.parseAsJson, 10)
  _ <- ZIO.foreach(testData, "in-topic") { record =>
    writer.publishValue(record, "in-topic")
  }
} yield ()
```

Now let's provide a little ZIO app which will read those records and write the enriched records to a new avro topic:

```scala
// our enrichment ETL relies on pulling a `DynamicProducer` to write to Kafka and `BatchedStream` to read from Kafka from the environment 
val enricher = for {
  writer <- ZIO.service[DynamicProducer]
  reader <- ZIO.service[BatchedStream]
  _ <- reader.withTopic("in-topic").foreachRecord { r =>

    // treat 'x' and 'y' as integers and add them:
    val sum = r.value.x.asInt() + r.value.y.asInt()

    // create a new json based on the original record but with a new 'sum' field
    // the '+' or 'merge' method is available on DymamicJson
    val newRecord = r.value() + (s"sum : ${sum}".parseAsJson)

    // now just squirt it into a new enriched topic:
    writer.publishValue(newRecord.asAvro("enriched.namespace"), "enriched-topic")
  }
} yield ()
```

That's it.

Franz uses a [typesafe config](https://github.com/lightbend/config) parsed by 'FranzConfig()', (which in turn as convenience methods such as 'withConsumerTopic(...)' as well).

So, running any of the above ZIO code "programs" might look like this:
```scala
val config = FranzConfig()
val appWithKafka = writeManyRecords.provideSomeLayer(config.kafkaLayer)
ZIO.scoped(appWithKafka).taskValue()
```

# Building
This project is built using [sbt](https://www.scala-sbt.org/):
```
sbt test
```

Or, if you don't have [sbt](https://www.scala-sbt.org/download.html) installed or just want a container-based build:
```
./dockerTest.sh
```

## Integration Testing

To test locally, the Kafka tests rely on (and assume) a locally running kafka, so use either

```
# assumes an M1 chip -- 'cause I'm selfish and opinionated and that's what I am currently using :-)
./startLocalKafka.sh  &
# or
./startLocalKafkaIntel.sh &

# followed by
sbt it:test
```

