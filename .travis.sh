#!/usr/bin/env bash

sbt fmtCheck compile test &&
    if $(test ${TRAVIS_REPO_SLUG} == "typedlabs/fs2-nats" && test ${TRAVIS_PULL_REQUEST} == "false" && test "$TRAVIS_TAG" != ""); then
      sbt +publishLocal
    else
      exit 0 # skipping publish, it's regular build
    fi

