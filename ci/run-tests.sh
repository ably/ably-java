#!/usr/bin/env bash

set -ex
export TERM=dumb

ret=0
../gradlew java:testRestSuite || ret=1
../gradlew java:testRealtimeSuite || ret=1
exit $ret
