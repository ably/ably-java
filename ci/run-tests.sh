#!/usr/bin/env bash

set -ex
export TERM=dumb

gradle java:testRestSuite
gradle java:testRealtimeSuite
