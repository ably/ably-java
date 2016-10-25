#!/usr/bin/env bash

set -ex
export TERM=dumb

ret=0
gradle java:testRestSuite || ret=1
gradle java:testRealtimeSuite || ret=1
exit $ret
