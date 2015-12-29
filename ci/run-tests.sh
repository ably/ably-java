#!/usr/bin/env bash

set -ex
export TERM=dumb

gradle lib:testRestSuite
gradle lib:testRealtimeSuite
