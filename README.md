# [Ably](https://www.ably.io)

| Android | Java |
|---------|------|
| [ ![Download](https://api.bintray.com/packages/ably-io/ably/ably-android/images/download.svg) ](https://bintray.com/ably-io/ably/ably-android/_latestVersion) | [ ![Download](https://api.bintray.com/packages/ably-io/ably/ably-java/images/download.svg) ](https://bintray.com/ably-io/ably/ably-java/_latestVersion) |

A Java Realtime and REST client library for [Ably Realtime](https://www.ably.io), the realtime messaging and data delivery service. You can visit the [Feature Support Matrix](https://www.ably.io/feature-support-matrix) to see the list of all the available features.

## Supported Platforms

This SDK supports the following platforms:

**Java:** Java 7+

**Android:** android-19 or newer as a target SDK, android-16 or newer as a target platform

We regression-test the library against a selection of Java and Android platforms (which will change over time, but usually consists of the versions that are supported upstream). Please refer to [.travis.yml](./.travis.yml) for the set of versions that currently undergo CI testing..

We'll happily support (and investigate reported problems with) any reasonably-widely-used platform, Java or Android.
If you find any compatibility issues, please [do raise an issue](https://github.com/ably/ably-java/issues/new) in this repository or [contact Ably customer support](https://support.ably.io/) for advice.

## Documentation

Visit https://www.ably.io/documentation for a complete API reference and more examples.

## Installation ##

Reference the library by including a compile dependency reference in your gradle build file.

For [Java](https://bintray.com/ably-io/ably/ably-java/_latestVersion):

```
compile 'io.ably:ably-java:1.0.11'
```

For [Android](https://bintray.com/ably-io/ably/ably-android/_latestVersion):

```
compile 'io.ably:ably-android:1.0.11'
```

The library is hosted on the [Jcenter repository](https://bintray.com/ably-io/ably), so you need to ensure that the repo is referenced also; IDEs will typically include this by default:

```
repositories {
	jcenter()
}
```

Previous releases of the Java library included a downloadable JAR; however we now only support installation via Maven/Gradle from the Jcenter repository. If you want to use a standalone fat JAR for (ie containing all dependencies), it can be generated via a gradle task (see [building](#building) below); note that this is the "Java" (JRE) library variant only; Android is now supported via an AAR and there is no self-contained AAR build option.

## Dependencies

For Java, JRE 7 or later is required. Note that the [Java Unlimited JCE extensions](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html)
must be installed in the Java runtime environment.

For Android, 4.0 (API level 14) or later is required.

## Using the Realtime API ##

### Introduction ###

Please refer to the [documentation](https://www.ably.io/documentation) for a full realtime API reference.

The examples below assume a client has been created as follows:

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

Please refer to the [documentation](https://www.ably.io/documentation) for a full REST API reference.

The examples below assume a client and/or channel has been created as follows:

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

## Building ##

The library consists of JRE-specific library (in `java/`) and an Android-specific library (in `android/`). The libraries are largely common-sourced; the `lib/` directory contains the common parts.

A gradle wrapper is included so these tasks can run without any prior installation of gradle. The Linux/OSX form of the commands, given below, is:

    ./gradlew <task name>

but on Windows there is a batch file:

    gradlew.bat <task name>

The JRE-specific library JAR is built with:

    ./gradlew java:jar

There is also a task to build a fat JAR containing the dependencies:

    ./gradlew java:fullJar

The Android-specific library AAR is built with:

    ./gradlew android:assemble

(The `ANDROID_HOME` environment variable must be set appropriately.)

## Tests

A gradle wrapper is included so these tasks can run without any prior installation of gradle. The Linux/OSX form of the commands, given below, is:

    ./gradlew <task name>

but on Windows there is a batch file:

    gradlew.bat <task name>

Tests are based on JUnit, and there are separate suites for the REST and Realtime libraries, with gradle tasks
for the JRE-specific library:

    ./gradlew java:testRestSuite

    ./gradlew java:testRealtimeSuite

To run tests against a specific host, specify in the environment:

    env ABLY_ENV=staging ./gradlew testRealtimeSuite

Tests will run against sandbox by default.

Tests can be run on the Android-specific library. An Android device must be connected,
either a real device or the Android emulator.

    ./gradlew android:connectedAndroidTest

## Developing this library with an IDE

The gradle project files can be imported to create projects in IntelliJ IDEA, Eclipse and Android Studio.

### Importing into IntelliJ

The top-level ably-java project can be imported into IntelliJ IDEA, enabling development of both the java and android projects. This has been tested with IntelliJ IDEA Ultimate 2017.2. To import into IDEA:

- do File->New->Project from Existing Sources...
- select ably-java/settings.gradle
- in the import dialog, check "Use auto-import" and uncheck "Create separate module per source set"
- select "ok"

This will create a project with separate java and android modules.

Interactive run/debug configurations to execute the unit tests can be created as follows:
- select Run->Edit configurations ...
- for the java project, create a new "JUnit" run configuration; or for the android project create a new "Android Instrumented Tests" configuration;
- select the Class as RealtimeSuite or RestSuite;
- select the relevant module for the classpath.

In order to run the Android configuration it is necessary to set up the Android SDK path by selecting a project of module and opening the module settings. The Android SDK needs to be added under Platform Settings->SDKs.

### Importing into Eclipse

The top-level ably-java project can be imported into Eclipse, enabling development of the java project only. The Eclipse Android development plugin (ADT) is no longer supported. This has been tested with Eclipse Oxygen.2

To import into Eclipse:

- do File->Import->Gradle->Existing Gradle project;
- follow the wizard steps, selecting the ably-java root directory.

This will create two projects in the workspace; one for the top-level ably-java project, and one for the java project.

Interactive run/debug configurations for the java project can be created as follows:
- select Run->Run configurations ...
- create a new JUnit configuration
- select the java project;
- select the Class as RealtimeSuite or RestSuite;
- select JUnit 4 as the test runner.

### Importing into Android studio

Android studio does not include the components required to support development of the java project, it is not capable of importing the multi-level ably-java gradle project. It is possible to import the android project as a standalone project into Android Studio by deleting the top-level settings.gradle file, which effectively decouples the android and java projects.

This has been tested with Android Studio 3.0.1.

To import into Android Studio:
- do Import project (Gradle, Eclipse ADT, etc);
- select ably-java/android/build.gradle;
- select OK to Gradle Sync.

This creates a single android project and module.

Configuration of Run/Debug configurations for running the unit tests on Android is the same as for IntelliJ IDEA (above).

## Release process

This library uses [semantic versioning](http://semver.org/). For each release, the following needs to be done:

### Release notes

* Create a branch for the release, named like `release-1.0.11`
* Replace all references of the current version number with the new version number (check this file [README.md](./README.md) and [common.gradle](./common.gradle)) and commit the changes
* Run [`github_changelog_generator`](https://github.com/skywinder/Github-Changelog-Generator) to update the [CHANGELOG](./CHANGELOG.md): `github_changelog_generator -u ably -p ably-java --header-label="# Changelog" --release-branch=release-1.0.11 --future-release=v1.0.8` 
* Commit [CHANGELOG](./CHANGELOG.md)
* Add a tag and push to origin such as `git tag v1.0.7; git push origin v1.0.7`
* Make a PR against `develop`
* Once the PR is approved, merge it into `develop`
* Fast-forward the master branch: `git checkout master && git merge --ff-only develop && git push origin master`

### Build release

* Run `gradle java:assemble` to build the JRE-specific JARs for this release
* Run `gradle android:assemble` to build the Android AAR for this release

### Publishing to JCenter (Maven)

* Go to the home page for the package; eg https://bintray.com/ably-io/ably/ably-java. Select [New version](https://bintray.com/ably-io/ably/ably-java/new/version), enter the new version such as "1.0.11" in name and save
* Run `./gradlew java:assembleRelease` locally to generate the files
* Open local relative folder such as `./java/build/release/1.0.11/io/ably/ably-java/1.0.11`
* Then go to the new version in JFrog Bintray; eg https://bintray.com/ably-io/ably/ably-java/1.0.11, then click on the link to upload via the UI in the "Upload files" section
* Type in `io/ably/ably-java/1.0.11` into "Target Repository Path" ensuring the correct version is included. The drag in the files in `java/build/release/1.0.11/`. Upload all the `.jar` files and the `.pom` file.
* You will see a notice "You have 4 unpublished item(s) for this version", make sure you click "Publish". Wait a few minutes and check that your version has all the necessary files at https://bintray.com/ably-io/ably/ably-java/1.0.11?sort=&order=#files/io/ably/ably-java/1.0.11 for example.
* Update the README text in Bintray.

### Create release on Github

* Visit [https://github.com/ably/ably-java/tags](https://github.com/ably/ably-java/tags) and `Add release notes` for the release including links to the changelog entry and the JCenter releases.

Similarly for the Android release at https://bintray.com/ably-io/ably/ably-android.
Run `gradle android:assembleRelease` locally to generate the files, and drag in the files in
`./android/build/release/1.0.11/io/ably/ably-android/1.0.11`. In this case upload the `.jar` files, the `.pom` file and the `.aar` file.

## Support, feedback and troubleshooting

Please visit http://support.ably.io/ for access to our knowledgebase and to ask for any assistance.

You can also view the [community reported Github issues](https://github.com/ably/ably-java/issues).

To see what has changed in recent versions of Bundler, see the [CHANGELOG](CHANGELOG.md).

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Ensure you have added suitable tests and the test suite is passing(`./gradlew java:testRestSuite java:testRealtimeSuite android:connectedAndroidTest`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request

## License

Copyright (c) 2015-2019 Ably Real-time Ltd, Licensed under the Apache License, Version 2.0.  Refer to [LICENSE](LICENSE) for the license terms.
