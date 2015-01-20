# Ably REST Java client library

## Overview

This is a generic Java client library for the Ably REST API. All parts of the
REST API are covered, including:

* authentication, and token generation;
* publication of messages on channels;
* getting application and channel history;
* getting application and channel usage statistics.

All IO is over HTTP and is blocking. The Apache HttpClient framework is used.

## Dependencies

JRE 6 or later is required.
All dependencies are included as libraries in `libs`.
Note that the [Java Unlimited JCE extensions](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html)
must be installed in the runtime environment.

## Building

Build a jar file for ably-rest with ant:

    ant clean && ant

Alternatively, Eclipse project and classpath files are included; import directly into Eclipse
(Indigo or later).

## Tests

Tests are based on JUnit, and a single JUnit suite (at `io.ably.test.RestSuite`)
covers all tests.

Tests can be run from Eclipse, or from the command-line by:

    java -classpath "./out/classes:./libs/*:./libs/test/*" io.ably.test.RestSuite

There is also an ant target to run the unit tests:

    ant test

To run tests against a specific host, specify this as a property on the ant commandline:

    ant test -Dably.host=localhost

Tests will run against staging by default.

Also, to force the tests to run without TLS, do

    ant test -Dably.tls=false

## Limitations

This library is still under development; expect some bugs.
