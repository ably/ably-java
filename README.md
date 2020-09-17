# [Ably](https://www.ably.io)

| Android | Java |
|---------|------|
| [ ![Download](https://api.bintray.com/packages/ably-io/ably/ably-android/images/download.svg) ](https://bintray.com/ably-io/ably/ably-android/_latestVersion) | [ ![Download](https://api.bintray.com/packages/ably-io/ably/ably-java/images/download.svg) ](https://bintray.com/ably-io/ably/ably-java/_latestVersion) |

A Java Realtime and REST client library for [Ably Realtime](https://www.ably.io), the realtime messaging and data delivery service. This library currently targets the [Ably client library features spec](https://www.ably.io/documentation/client-lib-development-guide/features/) Version 1.2. You can jump to the '[Known Limitations](#known-limitations)' section to see the features this client library does not yet support or [view our client library SDKs feature support matrix](https://www.ably.io/download/sdk-feature-support-matrix) to see the list of all the available features.

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
compile 'io.ably:ably-java:1.2.2'
```

For [Android](https://bintray.com/ably-io/ably/ably-android/_latestVersion):

```
compile 'io.ably:ably-android:1.2.2'
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

## Feature support

This library targets the Ably 1.1 client library specification and supports all principal 1.1 features.

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
	public void onMessage(Message message) {
		System.out.println("Received `" + message.name + "` message with data: " + message.data);
	}
});
```

or subscribe to certain events:

```java
String[] events = new String[] {"event1", "event2"};
channel.subscribe(events, new MessageListener() {
	@Override
	public void onMessage(Message message) {
		System.out.println("Received `" + message.name + "` message with data: " + message.data);
	}
});
```

### Subscribing to a channel in delta mode ###

Subscribing to a channel in delta mode enables [delta compression](https://www.ably.io/documentation/realtime/channels/channel-parameters/deltas). This is a way for a client to subscribe to a channel so that message payloads sent contain only the difference (ie the delta) between the present message and the previous message on the channel.

Request a Vcdiff formatted delta stream using channel options when you get the channel:

```java
Map<String, String> params = new HashMap<>();
params.put("delta", "vcdiff");
ChannelOptions options = new ChannelOptions();
options.params = params;
Channel channel = ably.channels.get("test", options);
```

Beyond specifying channel options, the rest is transparent and requires no further changes to your application. The `message.data` instances that are delivered to your `MessageListener` continue to contain the values that were originally published.

If you would like to inspect the `Message` instances in order to identify whether the `data` they present was rendered from a delta message from Ably then you can see if `extras.getDelta().getFormat()` equals `"vcdiff"`.

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

Given the message below

```java
Message message = new Message("myEvent", "Hello");
```

Sharing synchronously,

```java
channel.publish(message);
```

Sharing asynchronously,

```java
channel.publishAsync(message, new CompletionListener() {
  @Override
	public void onSuccess() {
	   System.out.println("Message successfully received by Ably server.");
	}

	@Override
	public void onError(ErrorInfo reason) {
		System.err.println("Unable to publish message to Ably server; err = " + reason.message);
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

### Logging ###

You can get log output from the library by modifying the log level:

```java
import io.ably.lib.util.Log;

ClientOptions opts = new ClientOptions(key);
opts.logLevel = Log.VERBOSE;
AblyRest ably = new AblyRest(opts);
...
```

By default, log output will go to `System.out` for the java library, and logcat for Android.

You can redirect the log output to a logger of your own by specifying a custom log handler:

```java
import io.ably.lib.util.Log.LogHandler;

ClientOptions opts = new ClientOptions(key);
opts.logHandler = new LogHandler() {
	public void println(int severity, String tag, String msg, Throwable tr) {
		/* handle log output here ... */
	}
};
AblyRest ably = new AblyRest(opts);
...
```

Note that any logger you specify in this way has global scope - it will set as a static of the library
and will apply to all Ably library instances. If you need to release your custom logger so that it can be
garbage-collected, you need to clear that static reference:

```java
import io.ably.lib.util.Log;

Log.setHandler(null);
```

## Using the Push API

### Delivering push notifications

See https://www.ably.io/documentation/general/push/publish for detail.

Ably provides two models for delivering push notifications to devices.

To publish a message to a channel including a push payload:

```
Message message = new Message("example", "realtime data");
message.extras = io.ably.lib.util.JsonUtils.object()
    .add("push", io.ably.lib.util.JsonUtils.object()
        .add("notification", io.ably.lib.util.JsonUtils.object()
            .add("title", "Hello from Ably!")
            .add("body", "Example push notification from Ably."))
        .add("data", io.ably.lib.util.JsonUtils.object()
            .add("foo", "bar")
            .add("baz", "qux")));

rest.channels.get("pushenabled:foo").publishAsync(message, new CompletionListener() {
    @Override
    public void onSuccess() {}

    @Override
    public void onError(ErrorInfo errorInfo) {
        // Handle error.
    }
});
```

To publish a push payload directly to a registered device:

```
Param[] recipient = new Param[]{new Param("deviceId", "xxxxxxxxxxx");

JsonObject payload = io.ably.lib.util.JsonUtils.object()
        .add("notification", io.ably.lib.util.JsonUtils.object()
            .add("title", "Hello from Ably!")
            .add("body", "Example push notification from Ably."))
        .add("data", io.ably.lib.util.JsonUtils.object()
            .add("foo", "bar")
            .add("baz", "qux")));

rest.push.admin.publishAsync(recipient, payload, , new CompletionListener() {
	 @Override
	 public void onSuccess() {}
 
	 @Override
	 public void onError(ErrorInfo errorInfo) {
		 // Handle error.
	 }
 });
```

### Activating a device and receiving notifications (Android only)

See https://www.ably.io/documentation/general/push/activate-subscribe for detail.
In order to enable an app as a recipent of Ably push messages:

- register your app with Firebase Cloud Messaging (FCM) and configure the FCM credentials in the app dashboard;
- include a service derived from `FirebaseMessagingService` and ensure it is started;
- include a method to handle registration notifications from Android, such as including a service derived from `AblyFirebaseInstanceIdService` and ensure it is started;
- initialise the device as an active push recipient:

```
realtime.setAndroidContext(context);
realtime.push.activate();
```

### Managing devices and subscriptions

See https://www.ably.io/documentation/general/push/admin for details of the push admin API.

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

Tests will run against the sandbox environment by default.

Tests can be run on the Android-specific library. An Android device must be connected,
either a real device or the Android emulator.

    ./gradlew android:connectedAndroidTest

We also have a small, fledgling set of unit tests which do not communicate with Ably's servers.
The plan is to expand this collection of tests in due course:

    ./gradlew java:runUnitTests

### Interactive push tests

End-to-end tests for push notifications (ie where the Android client is the target) can be tested interactively via a [separate app](https://github.com/ably/push-example-android).
There are [instructions there](https://github.com/ably/push-example-android#using-this-app-yourself) for setting up the necessary FCM account, configuring the credentials and other parameters,
in order to get end-to-end FCM notifications working.

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

1. Create a branch for the release, named like `release/1.2.2`
2. Replace all references of the current version number with the new version number (check this file [README.md](./README.md) and [common.gradle](./common.gradle)) and commit the changes
3. Run [`github_changelog_generator`](https://github.com/skywinder/Github-Changelog-Generator) to update the [CHANGELOG](./CHANGELOG.md):
    * This might work: `github_changelog_generator -u ably -p ably-java --header-label="# Changelog" --release-branch=release/1.2.2 --future-release=v1.2.2`
    * But your mileage may vary as it can error. Perhaps more reliable is something like: `github_changelog_generator -u ably -p ably-java --since-tag v1.2.1 --output delta.md` and then manually merge the delta contents in to the main change log
4. Commit [CHANGELOG](./CHANGELOG.md)
5. Make a PR against `main`
6. Once the PR is approved, merge it into `main`
7. Add a tag and push to origin - e.g.: `git tag v1.2.2 && git push origin v1.2.2`
8. Create the release on Github including populating the release notes (needed so JFrog can pull them in)
9. Assemble and Upload ([see below](#publishing-to-jcenter-and-maven-central) for details) - but the overall order to follow is:
	1. Upload to Bintray and use the pushed tag, which will pull in the associated release notes
	2. Comment out local `repository` lines in the two `maven.gradle` files temporarily (this is horrible but is [to be fixed soon](https://github.com/ably/ably-java/issues/566))
	3. Repeat the assemble stages to this time to push to Maven Central

### Signing

If you've not configured the signing key in your [Gradle properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties) then release builds will complain:

    Cannot perform signing task ':java:signArchives' because it has no configured signatory

You need to [configure Signatory credentials](https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials), for example via the `gradle.properties` file in your `GRADLE_USER_HOME` folder (usually `~/.gradle`).

The GPG key file is internal and private to Ably.

### Publishing to JCenter and Maven Central

We publish to:

* JCenter via JFrog's [Bintray](https://bintray.com/ably-io/ably)
* Maven Central via Sonatype's [OSSRH](https://issues.sonatype.org/browse/OSSRH-52871) / [Nexus](https://oss.sonatype.org/#nexus-search;quick~io.ably)

#### Releasing to JCenter (JFrog Bintray)

The `java` release process goes as follows:

* Go to the home page for the package; eg https://bintray.com/ably-io/ably/ably-java. Select Add a version, enter the new version such as "1.2.2" in name and save
* Run `./gradlew java:assembleRelease` locally to generate the files
* Open local relative folder in Finder, such as `./java/build/release/1.2.2/io/ably/ably-java/1.2.2`
* Go to the new version in JFrog Bintray; eg https://bintray.com/ably-io/ably/ably-java/1.2.2, then click on the link to upload via the UI in the "Upload files" section
* Drag in the files from Finder, just the `.jar` files and the `.pom` file. JFrog will fill in the "Target Path" box after you drop the files in. Click the "Upload" button.
* You will see a notice something like "4 unpublished files in your version. Will be deleted in 6 days and 22 hours. Publish all or Delete all unpublished files.", make sure you click "Publish all". Wait a few minutes and check that what's uploaded looks like what was uploaded for previous releases. The `maven-metadata` files are created by JFrog.
* Update the README text in Bintray (version number needs incrementing).

Similarly for the `android` release at https://bintray.com/ably-io/ably/ably-android:

* Run `./gradlew android:assembleRelease` locally to generate the files, and drag in the files in
`./android/build/release/1.2.2/io/ably/ably-android/1.2.2`.
* In this case upload the `.jar` files, the `.pom` file and the `.aar` file.

#### Releasing to Maven Central (Sonatype Nexus)

Bearing in mind the earlier instructions around commenting out lines in the `maven.gradle` files (temporary requirement) you then need to find the new staging repository in
[Nexus Repository Manager](https://oss.sonatype.org/#stagingRepositories)
and do a few things with it:

1. Check that it contains Android and Java releases.
2. "Close" it - this will take a few minutes during which time it will say (after a refresh of your browser) that "Activity: Operation in Progress".
3. Once it has closed you will have "Release" available. You can allow it to "automatically drop" after successful release. A refresh or two later of the browser and the staging repository will have disappeared from the list (i.e. it's been dropped which implies it was released successfully).
4. A [search for Ably packages](https://oss.sonatype.org/#nexus-search;quick~io.ably) should now list the new version for both `ably-android` and `ably-java`.

### Creating the release on Github

Visit [https://github.com/ably/ably-java/tags](https://github.com/ably/ably-java/tags) and `Add release notes` for the release including links to the changelog entry and the JCenter releases.

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
