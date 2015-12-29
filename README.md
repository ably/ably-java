# [Ably](https://www.ably.io)

[![Build Status](https://travis-ci.org/ably/ably-java.png)](https://travis-ci.org/ably/ably-java)

A Java Realtime and REST client library for [Ably.io](https://www.ably.io), the realtime messaging service.

## Documentation

Visit https://www.ably.io/documentation for a complete API reference and more examples.

## Using the Realtime and REST API

Please refer to the [documentation](https://www.ably.io/documentation).

## Dependencies

JRE 7 or later is required.
Note that the [Java Unlimited JCE extensions](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html)
must be installed in the runtime environment.

## Building

The library consists of a generic java library (in `lib/`) and a separate Android test project (in `android-test/`).
The base library jar is built with:

    gradle lib:jar

There is also a task to build a fat jar containing the dependencies:

    gradle fullJar

## Tests

Tests are based on JUnit, and there are separate suites for the REST and Realtime libraries, with gradle tasks:

    gradle testRestSuite

    gradle testRealtimeSuite

To run tests against a specific host, specify in the environment:

    export ABLY_ENV=staging; gradle testRealtimeSuite

Tests will run against sandbox by default.

## Support, feedback and troubleshooting

Please visit http://support.ably.io/ for access to our knowledgebase and to ask for any assistance.

You can also view the [community reported Github issues](https://github.com/ably/ably-java/issues).

To see what has changed in recent versions of Bundler, see the [CHANGELOG](CHANGELOG.md).

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Ensure you have added suitable tests and the test suite is passing(`ant test`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request

## License

Copyright (c) 2015 Ably Real-time Ltd, Licensed under the Apache License, Version 2.0.  Refer to [LICENSE](LICENSE) for the license terms.
