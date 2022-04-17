#!/usr/bin/env bash

# This is just a convenience script should anybody want to test locally if they don't have sbt installed
#
# See https://hub.docker.com/r/hseeberger/scala-sbt/
docker run -it --rm -u sbtuser \
  -w /code \
  -v $(pwd):/code \
  -v "$HOME"/.ivy2:/home/sbtuser/.ivy2 \
  -v "$HOME"/.sbt:/home/sbtuser/.sbt \
  -v "$HOME"/.m2:/home/sbtuser/.m2 \
  hseeberger/scala-sbt:8u222_1.3.5_2.13.1 \
  sbt test