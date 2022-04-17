# Franz 
![https://github.com/aaronp/franz/actions](https://github.com/aaronp/franz/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/franz_3/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/franz_3)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Purpose

The aim is to improve on the example given by [47deg here](https://www.47deg.com/blog/run-fs2-kafka-scala-cli/).

That is, can we treat scala like a dynamically-typed/scripted language and avoid the (1) build a library/image (2) update devops pipelines w/ that version.

And instead just have a terse, single-screen business logic script so that we can throw away step #1 and make the whole code fit into a ConfigMap by leveraging e.g. [scala-cli](https://scala-cli.virtuslab.org/)?



## make working with streaming data as easy as possible:
1. mapping/filtering/flat-mapping with common data types (e.g. avro, protobuf, schema registry)
2. going into/out-of sinks (kafka, endpoints, databases, file-systems, stdout)
3. make all this really easy - no extra CI/CD, building new images, etc.


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