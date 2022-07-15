# [Ably](https://www.ably.io)

[![.github/workflows/check.yml](https://github.com/ably/ably-java/workflows/.github/workflows/check.yml/badge.svg)](https://github.com/ably/ably-java/actions/workflows/check.yml)
[![.github/workflows/integration-test.yml](https://github.com/ably/ably-java/workflows/.github/workflows/integration-test.yml/badge.svg)](https://github.com/ably/ably-java/actions/workflows/integration-test.yml)
[![.github/workflows/emulate.yml](https://github.com/ably/ably-java/actions/workflows/emulate.yml/badge.svg)](https://github.com/ably/ably-java/actions/workflows/emulate.yml)

_[Ably](https://ably.com) is the platform that powers synchronized digital experiences in realtime. Whether attending an event in a virtual venue, receiving realtime financial information, or monitoring live car performance data – consumers simply expect realtime digital experiences as standard. Ably provides a suite of APIs to build, extend, and deliver powerful digital experiences in realtime for more than 250 million devices across 80 countries each month. Organizations like Bloomberg, HubSpot, Verizon, and Hopin depend on Ably’s platform to offload the growing complexity of business-critical realtime data synchronization at global scale. For more information, see the [Ably documentation](https://ably.com/documentation)._

## Overview

A Java Realtime and REST client library.
This library currently targets the [Ably client library features spec](https://www.ably.com/docs/client-lib-development-guide/features/) Version 1.2.

## Installation

Include the library by adding an `implementation` reference to `dependencies` block in your [Gradle](https://gradle.org/) build script.

For [Java](https://mvnrepository.com/artifact/io.ably/ably-java/latest):

```groovy
implementation 'io.ably:ably-java:1.2.12'
```

For [Android](https://mvnrepository.com/artifact/io.ably/ably-android/latest):

```groovy
implementation 'io.ably:ably-android:1.2.12'
```

The library is hosted on [Maven Central](https://mvnrepository.com/repos/central), so you need to ensure that the repository is referenced also; IDEs will typically include this by default:

```groovy
repositories {
	mavenCentral()
}
```

We only support installation via Maven / Gradle from the Maven Central repository. Checkout [requirements](#requirements).

## Runtime Requirements

The library requires that the runtime environment is able to establish a safe TLS connection (TLS v1.2 or v1.3). It will fail to connect with a `SecurityException` if this level of security is not available.

## Usage

Please refer to the [documentation](https://www.ably.com/docs) for a full API reference.

### Using the Realtime API

The examples below assume a client has been created as follows:

```java
AblyRealtime ably = new AblyRealtime("xxxxx");
```

#### Connection

AblyRealtime will attempt to connect automatically once new instance is created. Also, it offers API for listening connection state changes.

```java
ably.connection.on(new ConnectionStateListener() {
	@Override
	public void onConnectionStateChanged(ConnectionStateChange state) {
		System.out.println("New state is " + state.current.name());
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

#### Subscribing to a channel

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

#### Subscribing to a channel in delta mode

Subscribing to a channel in delta mode enables [delta compression](https://www.ably.com/docs/realtime/channels/channel-parameters/deltas). This is a way for a client to subscribe to a channel so that message payloads sent contain only the difference (ie the delta) between the present message and the previous message on the channel.

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

#### Publishing to a channel

Data published to a channel (apart from strings or bytearrays) has to be instances of JsonElement to be encoded properly.

```java
// Publishing message of type String
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

// Publishing message of type JsonElement
JsonObject jsonElement = new JsonObject();

Map<String, String> inputMap = new HashMap<String, String>();
inputMap.put("name", "Joe");
inputMap.put("surename", "Doe");

for (Map.Entry<String, String> entry : inputMap.entrySet()) {
    jsonElement.addProperty(entry.getKey(), entry.getValue());
}

channel.publish("greeting", message, new CompletionListener() {
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

#### Querying the history

```java
PaginatedResult<Message> result = channel.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
	result = result.getNext();
	System.out.println(result.items().length + " messages received in next page");
}
```

#### Presence on a channel

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

#### Querying the presence history

```java
PaginatedResult<PresenceMessage> result = channel.presence.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
	result = result.getNext();
	System.out.println(result.items().length + " messages received in next page");
}
```

#### Channel state

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

#### Use of authCallback

Callback that provides either tokens (`TokenDetails`), or signed token requests (`TokenRequest`), in response to a request with given token params.

```java
ClientOptions options = new ClientOptions();
    
options.authCallback = new Auth.TokenCallback() {
    @Override
    public Object getTokenRequest(Auth.TokenParams params) {
        System.out.println("Token Params: " + params);
        // TODO: process params
        return null; // TODO: return TokenDetails or TokenRequest or JWT string
    }
};

AblyRealtime ablyRealtime = new AblyRealtime(options);
```

### Using the REST API

The examples below assume a client and/or channel has been created as follows:

```java
AblyRest ably = new AblyRest("xxxxx");
Channel channel = ably.channels.get("test");
```

#### Publishing a message to a channel

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

#### Querying the history

```java
PaginatedResult<Message> result = channel.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
	result = result.getNext();
	System.out.println(result.items().length + " messages received in next page");
}
```

#### Presence on a channel

```java
PaginatedResult<PresenceMessage> result = channel.presence.get(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
	result = result.getNext();
	System.out.println(result.items().length + " messages received in next page");
}
```

#### Querying the presence history

```java
PaginatedResult<PresenceMessage> result = channel.presence.history(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
	result = result.getNext();
	System.out.println(result.items().length + " messages received in next page");
}
```

#### Generate a Token and Token Request

```java
TokenDetails tokenDetails = ably.auth.requestToken(null, null);
System.out.println("Success; token = " + tokenRequest);
```

#### Fetching your application's stats

```java
PaginatedResult<Stats> stats = ably.stats(null);

System.out.println(result.items().length + " messages received in first page");
while(result.hasNext()) {
	result = result.getNext();
	System.out.println(result.items().length + " messages received in next page");
}
```

#### Fetching the Ably service time

```java
long serviceTime = ably.time();
```

#### Logging

You can get log output from the library by modifying the log level:

```java
import io.ably.core.util.Log;

ClientOptions opts = new ClientOptions(key);
opts.logLevel = Log.VERBOSE;
AblyRest ably = new AblyRest(opts);
...
```

By default, log output will go to `System.out` for the java library, and logcat for Android.

You can redirect the log output to a logger of your own by specifying a custom log handler:

```java
import io.ably.core.util.Log.LogHandler;

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
import io.ably.core.util.Log;

Log.setHandler(null);
```

### Using the Push API

#### Delivering push notifications

See [documentation](https://www.ably.com/docs/general/push/publish)  for detail.

Ably provides two models for delivering push notifications to devices.

To publish a message to a channel including a push payload:

```java
Message message = new Message("example", "realtime data");
message.extras = io.ably.core.util.JsonUtils.object()
    .add("push", io.ably.core.util.JsonUtils.object()
        .add("notification", io.ably.core.util.JsonUtils.object()
            .add("title", "Hello from Ably!")
            .add("body", "Example push notification from Ably."))
        .add("data", io.ably.core.util.JsonUtils.object()
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

```java
Param[] recipient = new Param[]{new Param("deviceId", "xxxxxxxxxxx");

JsonObject payload = io.ably.core.util.JsonUtils.object()
        .add("notification", io.ably.core.util.JsonUtils.object()
            .add("title", "Hello from Ably!")
            .add("body", "Example push notification from Ably."))
        .add("data", io.ably.core.util.JsonUtils.object()
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

#### Activating a device and receiving notifications (Android only)

See https://www.ably.com/docs/general/push/activate-subscribe for detail.
In order to enable an app as a recipient of Ably push messages:

- register your app with Firebase Cloud Messaging (FCM) and configure the FCM credentials in the app dashboard;
- Implement a service extending [`FirebaseMessagingService`](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService) and ensure it is declared in your `AndroidManifest.xml`, as per [Firebase's guide: Edit your app manifest](https://firebase.google.com/docs/cloud-messaging/android/client#manifest);
  - Override [`onNewToken`](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService#public-void-onnewtoken-string-token), and provide Ably with the registration token: `ActivationContext.getActivationContext(this).onNewRegistrationToken(RegistrationToken.Type.FCM, token);`. This method will be called whenever a new token is provided by Android.
- Activate the device for push notifications:

```java
realtime.setAndroidContext(context);
realtime.push.activate();
```

## Resources

Visit https://www.ably.com/docs for a complete API reference and more examples.

### Example projects:

- [Ably Asset Tracking SDKs for Android](https://github.com/ably/ably-asset-tracking-android/blob/main/README.md#useful-resources)
- [Chat app using Spring Boot + Auth0 + Ably](https://github.com/ably-labs/spring-boot-auth0)
- [Spring + Ably Pub/Sub Demo with a Collaborative TODO list](https://github.com/ably-labs/ably-spring-pubsub)

## Requirements

For Java, JRE 7 or later is required. Note that the [Java Unlimited JCE extensions](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html) must be installed in the Java runtime environment.

For Android, 4.4 (API level 19) or later is required.

## Support, feedback and troubleshooting

Please visit http://support.ably.io/ for access to our knowledgebase and to ask for any assistance.

You can also view the [community reported Github issues](https://github.com/ably/ably-java/issues).

To see what has changed in recent versions of Bundler, see the [CHANGELOG](CHANGELOG.md).

## Contributing

For guidance on how to contribute to this project, see [CONTRIBUTING.md](CONTRIBUTING.md).
