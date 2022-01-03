![build](https://github.com/aaronp/code-template/actions/workflows/ci.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/code-template_3/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/code-template_3)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# About - the 'why' idea

### TL;DR;

Write [serverless-like](https://en.wikipedia.org/wiki/Serverless_computing) applications which have the ability to actually reach stability.

That is, just drop-in the business logic script within some applications without having to change those applications.

Any time we have to create a new app, such as:
 * a REST service which can execute some business logic for fixed endpoints
 * a steams (kafka, rabbit, whatever) app to do some ETL
 * a batch/cron job which can do all the authentication/context prep and then just run some logic
 
We have some overhead before we even do anything:
 * create a repo
 * put in the CI/CD
 * documentation
 * the SDLC stuff (dev/test/release)

We should be able to do better - to offer an "insert here" business-logic in a lof our applications.

To read more, see [here](about.md).

# Building

This project is built using [sbt](https://www.scala-sbt.org/):
```
sbt test
```

Otherwise if you want to play around (and have docker installed) for a container-based experience:
```
./dockerTest.sh
```