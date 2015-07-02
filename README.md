# [Ably](https://www.ably.io)

[![Build Status](https://travis-ci.org/ably/ably-java.png)](https://travis-ci.org/ably/ably-java)

A Java Realtime and REST client library for [Ably.io](https://www.ably.io), the realtime messaging service.

## Documentation

Visit https://www.ably.io/documentation for a complete API reference and more examples.

## Using the Realtime and REST API

Please refer to the [documentation](https://www.ably.io/documentation).

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

## Support, feedback and troubleshooting

Please visit http://support.ably.io/ for access to our knowledgebase and to ask for any assistance.

You can also view the [community reported Github issues](https://github.com/ably/ably-java/issues).

To see what has changed in recent versions of Bundler, see the [CHANGELOG](CHANGELOG.md).

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Ensure you have added suitable tests and the test suite is passing(`bundle exec rspec`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request

## License

Copyright (c) 2015 Ably Real-time Ltd, Licensed under the Apache License, Version 2.0.  Refer to [LICENSE](LICENSE) for the license terms.
