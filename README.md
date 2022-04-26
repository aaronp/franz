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

*How*: By using ZIO
TL;DR: Instead of "Future[A]" think "IO[A]" where 'IO' can be rerun/restarted, raced, fork/joined/cancelled, and a bunch of other stuff.

3. Provide this as a library, so [we can completely remove the separate CI/CD overheads](https://www.47deg.com/blog/run-fs2-kafka-scala-cli/)

Because we have powerful constructs for our code:
 * mapping/filtering/flat-mapping with common data types (e.g. avro, protobuf, schema registry)
 * going into/out-of sinks (kafka, endpoints, databases, file-systems, stdout)

We can create great tooling which interops w/ databases, REST services, etc in a few lines of code

## Example
```aidl
      val config = FranzConfig()

      val jasonData = """
            x : 1
            y : 2""".parseAsJson
      val myApp = for {
        // populate the input topic:
        _ <- publishValue(,
                          inTopic)
        _ <- publishValue("""x : 4
            |y : 10""".stripMargin.parseAsJson,
                          inTopic)
        // map that topic into a new one
        inStream <- config.batchedStream
        _ <- inStream
          .withTopic(inTopic)
          .foreachRecord { r =>
            // to me, this isn't too bad for a DSL:
            val record = r.value() // get the record's value

            // treat 'x' and 'y' as integers:
            val sum = record.x.asInt() + record.y.asInt()

            // create a new record w/ the new 'sum' field
            val newRecord = record + (s"sum : ${sum}".parseAsJson)

            // and squirt it into our sumTopic:
            publishValue(newRecord.asAvro("sum"), sumTopic)
          }
          .runDrain
          .fork

        // now, for our test, read two records from the 'sum' topic
        sumStream <- config.batchedStream
        chunk     <- sumStream.withTopic(sumTopic).kafkaStream.run(ZSink.take(2))

        // some housekeeping (delete our topics)
        admin <- config.admin
        _     <- admin.deleteTopic(inTopic)
        _     <- admin.deleteTopic(sumTopic)
      } yield chunk.toList.map(_.record.value())
```


# TODO
 * add 'fromEarliest', 'fromLatest', 'fromOffset(x)' and 'fromTime(xyz)' to FranzConfig
 * example from one topic to another


# Building
This project is built using [sbt](https://www.scala-sbt.org/):
```
sbt test
```

Otherwise if you want to play around (and have docker installed) for a container-based experience:
```
./dockerTest.sh
```