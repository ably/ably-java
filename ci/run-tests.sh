#!/usr/bin/env bash

set -ex
export TERM=dumb

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ret=0
$DIR/../gradlew java:testRestSuite || ret=1
$DIR/../gradlew java:testRealtimeSuite || ret=1
exit $ret
