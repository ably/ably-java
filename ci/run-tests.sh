#!/usr/bin/env bash

set -ex

gradle lib:testRestSuite
gradle lib:testRealtimeSuite
