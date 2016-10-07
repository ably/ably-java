# [Ably](https://www.ably.io)

[ ![Download](https://api.bintray.com/packages/ably-io/ably/java/images/download.svg) ](https://bintray.com/ably-io/ably/java/_latestVersion)

A Java Realtime and REST client library for [Ably.io](https://www.ably.io), the realtime messaging service.

## Documentation

Visit https://www.ably.io/documentation for a complete API reference and more examples.

## Using the Realtime and REST API

The Realtime library for Java is downloadable as a JAR at our [Github releases page](https://github.com/ably/ably-java/releases). You can either download the full JAR which includes all dependencies, or just the library but it will be your responsibility to ensure alld dependencies are met.

Please refer to the [documentation](https://www.ably.io/documentation).

## Dependencies

JRE 7 or later is required.
Note that the [Java Unlimited JCE extensions](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html)
must be installed in the runtime environment.

## Building ##

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

    env ABLY_ENV=staging gradle testRealtimeSuite

Tests will run against sandbox by default.

## Installation ##

Download [the latest JAR](https://github.com/ably/ably-java/releases) or grab via Gradle:

```groovy
compile 'io.ably:ably-java:0.8.4'
```

and add following repo for a sub-dependency,

```groovy
maven { url "https://raw.github.com/paddybyers/Java-WebSocket/mvn-repo/" }
```

## Using the Realtime API ##

### Introduction ###

All examples assume a client has been created as follows:

```java
AblyRealtime ably = new AblyRealtime("xxxxx");
```

### Connection ###

AblyRealtime will attempt to connect automatically once new instance is created. Also, it offers API for listening connection state changes.

```java
ably.connection.on(new ConnectionStateListener() {
	@Override
	public void onConnectionStateChanged(ConnectionStateChange state) {
    System.out.println("New state is " + change.current.name());

		switch (state.current) {
			case connected: {
				// Successful connection
				break;
			}
			case failed: {
				// Failed connection
				break;
			}
		}
	}
});
```

And it offers API for listening specific connection state changes.

```java
ably.connection.on(ConnectionState.connected, new ConnectionStateListener() {
	@Override
	public void onConnectionStateChanged(ConnectionStateChange state) {
		/* Do something */
	}
});
```

### Subscribing to a channel ###

Given:

```java
Channel channel = ably.channels.get("test");
```

Subscribe to all events:

```java
channel.subscribe(new MessageListener() {
	@Override
	public void onMessage(Message[] messages) {
    for(Message message : messages) {
			System.out.println("Received `" + message.name + "` message with data: " + message.data);
		}
	}
});
```

or subscribe to certain events:

```java
String[] events = new String[] {"event1", "event2"};
channel.subscribe(events, new MessageListener() {
	@Override
	public void onMessage(Message[] messages) {
    System.out.println("Received `" + messages[0].name + "` message with data: " + message[0].data);
	}
});
```

### Publishing to a channel ###

```java
channel.publish("greeting", "Hello World!", new CompletionListener() {
	@Override
	public void onSuccess() {
    System.out.println("Message successfully sent");
	}

	@Override
	public void onError(ErrorInfo reason) {
    System.err.println("Unable to publish message; err = " + reason.message);
	}
});
```

### Querying the history ###

```java
PaginatedResult<Message> result = channel.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
  result = result.getNext();
  System.out.println(result.items().length + " messages received in next page");
}
```

### Presence on a channel ###

```java
channel.presence.enter("john.doe", new CompletionListener() {
	@Override
	public void onSuccess() {
		// Successfully entered to the channel
	}

	@Override
	public void onError(ErrorInfo reason) {
		// Failed to enter channel
	}
});
```

### Querying the presence history ###

```java
PaginatedResult<PresenceMessage> result = channel.presence.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
  result = result.getNext();
  System.out.println(result.items().length + " messages received in next page");
}
```

### Channel state ###

`Channel` extends `EventEmitter` that emits channel state changes, and listening those events is possible with `ChannelStateListener`

```java
ChannelStateListener listener = new ChannelStateListener() {
	@Override
	public void onChannelStateChanged(ChannelState state, ErrorInfo reason) {
		System.out.println("Channel state changed to " + state.name());
		if (reason != null) System.out.println(reason.toString());
	}
};
```

You can register using

```java
channel.on(listener);
```

and after you are done listening channel state events, you can unregister using
```java
channel.off(listener);
```

If you are interested with specific events, it is possible with providing extra `ChannelState` value.

```java
channel.on(ChannelState.attached, listener);
```


## Using the REST API ##

### Introduction ###

All examples assume a client and/or channel has been created as follows:

```java
AblyRest ably = new AblyRest("xxxxx");
Channel channel = ably.channels.get("test");
```

### Publishing a message to a channel ###

Given messages below

```java
Message[] messages = new Message[]{new Message("myEvent", "Hello")};
```

Sharing synchronously,

```java
channel.publish(messages);
```
Sharing asynchronously,

```java
channel.publishAsync(messages, new CompletionListener() {
  @Override
	public void onSuccess() {
	   System.out.println("Message successfully sent");
	}

	@Override
	public void onError(ErrorInfo reason) {
    System.err.println("Unable to publish message; err = " + reason.message);
	}
});
```


### Querying the history ###

```java
PaginatedResult<Message> result = channel.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
  result = result.getNext();
  System.out.println(result.items().length + " messages received in next page");
}
```

### Presence on a channel ###

```java
PaginatedResult<PresenceMessage> result = channel.presence.get(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
  result = result.getNext();
  System.out.println(result.items().length + " messages received in next page");
}
```

### Querying the presence history ###

```java
PaginatedResult<PresenceMessage> result = channel.presence.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
  result = result.getNext();
  System.out.println(result.items().length + " messages received in next page");
}
```

### Generate a Token and Token Request ###

```java
TokenDetails tokenDetails = ably.auth.requestToken(null, null);
System.out.println("Success; token = " + tokenRequest);
```

### Fetching your application's stats ###

```java
PaginatedResult<Stats> stats = ably.stats(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
  result = result.getNext();
  System.out.println(result.items().length + " messages received in next page");
}
```

### Fetching the Ably service time ###

```java
long serviceTime = ably.time();
```

## Release notes

This library uses [semantic versioning](http://semver.org/). For each release, the following needs to be done:

* Replace all references of the current version number with the new version number (check [pom.xml](./pom.xml) and [build.gradle](.build.gradle)) and commit the changes
* Run [`github_changelog_generator`](https://github.com/skywinder/Github-Changelog-Generator) to automate the update of the [CHANGELOG](./CHANGELOG.md). Once the CHANGELOG has completed, manually change the `Unreleased` heading and link with the current version number such as `v0.8.4`. Also ensure that the `Full Changelog` link points to the new version tag instead of the `HEAD`. Commit this change.
* Add a tag and push to origin such as `git tag v0.8.4 && git push origin v0.8.4`
* Run `gradle lib:jar && gradle fullJar` to build the JARs for this release
* Visit [https://github.com/ably/ably-java/tags](https://github.com/ably/ably-java/tags) and `Add release notes` for the release, then attach the generated JARs in the folder `lib/build/libs`

### Publishing to JCentre (Maven)

* Go to the home page https://bintray.com/ably-io/ably/java. Select [New version](https://bintray.com/ably-io/ably/java/new/version), enter the new version such as "0.8.4" in name and save
* Run `gradle generateRelease uploadArchives` locally to generate the files
* Open local relative folder such as `/lib/build/release/0.8.4/`
* Then go to the new version in JFrog Bintray and go to files such as https://bintray.com/ably-io/ably/java/0.8.4#files/io/ably/ably-java, then click on Upload files
* Type in `io/ably/ably-java/0.8.4` into "Target Repository Path" ensuring the correct version is included. The drag in the files in `/lib/build/release/0.8.4/`
* You will see a notice "You have 8 unpublished item(s) for this version", make sure you click "Publish". Wait a few minutes and check that your version has all the necessary files at https://bintray.com/ably-io/ably/java/v0.8.4?sort=&order=#files/io/ably/ably-java/0.8.4 for example.

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
