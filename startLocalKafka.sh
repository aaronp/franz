#!/usr/bin/env bash
docker run --rm -p 2181:2181 -p 3030:3030 -p 8081-8083:8081-8083 \
           -p 9581-9585:9581-9585 -p 9092:9092 -e ADV_HOST=$(ipconfig getifaddr en0) \
           faberchri/fast-data-dev

# The lensesio has an open issue for ARM (Apple M1) chips,
# so we're not using this image:  lensesio/fast-data-dev:latest
#
# ATM (Apr 2022) the work around is to build the above image:
# This was the instruction: https://github.com/lensesio/fast-data-dev/issues/175#issuecomment-947001807
#
# git clone https://github.com/faberchri/fast-data-dev.git
# cd fast-data-dev
# docker build -t faberchri/fast-data-dev .
# docker run --rm -p 3030:3030 faberchri/fast-data-dev
#
# or use this docker-compose alternative:
# https://github.com/FilipeNavas/kafka-local