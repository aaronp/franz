# Franz 
![https://github.com/aaronp/franz/actions](https://github.com/aaronp/franz/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/franz_3/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/franz_3)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Purpose

## make working with streaming data as easy as possible:
1. mapping/filtering/flat-mapping with common data types (e.g. avro, protobuf, schema registry)
2. going into/out-of sinks (kafka, endpoints, databases, file-systems, stdout)
3. make all this really easy - no extra CI/CD, building new images, etc.

### Mapping data
you can't just map avro (or any generic data types), as you have to provide serialisation 

We also want to address data as easily as json for these dynamic types:

e.g. just push:
```
{
 foo : {
   bar : [1, 2, 3]
  }
}  
```

and use it:
```
sum = message.body.foo.bar[0] + message.body.foo.bar[1]
```

### Sources / Sinks
We want strong resource guarantees for data integrity, concurrency, etc.

### Make it as easy as possible!

typical code-bases would have:
 * some config
 * some config parsing (properties, env variables, whatever)
 * some combination of data plumbing and business logic (and all the serde stuff)
 * bundle all that up for different environments and CI/CD it
 
Instead, we should be able to expose all that as easy as writing javascript in a browser (but with good concurrency/resource/data integrity guarantees):

```
> cat script.sc
batch.foreach { msg =>
    val value = msg.content.value
    for {
      updates      <- sql"insert into Materialised (kind, id, version, record) values (${msg.topic}, ${msg.key.asString}, ${msg.offset}, ${value.noSpaces})".run
      url          = s"http://some-api:8080/rest/endpoint"/${msg.partition}/${msg.offset}"
      postResponse <- post(url, msg.key.deepMerge(msg.content.value))
      dataResp     <- post({
                        foo : value.data.foo
                        bar : value.data.bar
                      })
    } yield ()
  }

docker run kafkatool -e KAFKA_BROKERES=foo:1234 script.sc
```


# Building
This project is built using [sbt](https://www.scala-sbt.org/):
```
sbt test
```

Otherwise if you want to play around (and have docker installed) for a container-based experience:
```
./dockerTest.sh
```