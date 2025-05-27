# Ably Pub/Sub Java SDK

Build any realtime experience using Ably’s Pub/Sub Java SDK. Supported on all popular platforms and frameworks, including Kotlin and Android.

Ably Pub/Sub provides flexible APIs that deliver features such as pub-sub messaging, message history, presence, and push notifications. Utilizing Ably’s realtime messaging platform, applications benefit from its highly performant, reliable, and scalable infrastructure.

Find out more:

* [Ably Pub/Sub docs](https://ably.com/docs/basics)
* [Ably Pub/Sub examples](https://ably.com/examples?product=pubsub)

---

## Getting started

Everything you need to get started with Ably:

- [Quickstart in Pub/Sub using Java](https://ably.com/docs/getting-started/quickstart?lang=java)

---

## Supported platforms

Ably aims to support a wide range of platforms. If you experience any compatibility issues, open an issue in the repository or contact [Ably support](https://ably.com/support).

The following platforms are supported:

| Platform | Support |
|----------|---------|
| Java | >=8 (JRE 1.8 or later) |
| Kotlin | >= 2.1.10 |
| Android | >=4.4 (API level 19) |
| [Proxy environments](#proxy-support) | Supported via `network-client-okhttp` module |

> [!IMPORTANT]
> SDK versions < 1.2.35 will be [deprecated](https://ably.com/docs/platform/deprecate/protocol-v1) from November 1, 2025.

---

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
  public void onChannelStateChanged(ChannelStateChange stateChange) {
    System.out.println("Channel state changed to " + stateChange.current.name());
    if (stateChange.reason != null)
        System.out.println("Channel state error" + stateChange.reason.message);
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

#### Threads

AblyRealtime will invoke all callbacks on background thread. 
If you are using Ably in Android application you must switch to main thread to update UI. 

```java
channel.presence.enter("john.doe", new CompletionListener() {
    @Override
    public void onSuccess() {
        //If you are in Activity
        runOnUiThread(new Runnable() {
        @Override
        public void run() {
                //Update your UI here
            }
        });
        
        //If you are in Fragment or other class
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                //Update your UI here
            }
        });
    }
});
```

### Using the Push API

#### Delivering push notifications

See [documentation](https://www.ably.com/docs/general/push/publish)  for detail.

Ably provides two models for delivering push notifications to devices.

To publish a message to a channel including a push payload:

```java
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

```java
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

## Using Ably SDK Under a Proxy

When working in environments where outbound internet access is restricted, such as behind a corporate proxy, the Ably SDK allows you to configure a proxy server for HTTP and WebSocket connections.

### Add the Required Dependency

You need to use **OkHttp** library for making HTTP calls and WebSocket connections in the Ably SDK to get proxy support both for your Rest and Realtime clients.

Add the following dependency to your `build.gradle` file:

```groovy
dependencies {
    runtimeOnly("io.ably:network-client-okhttp:1.2.53")
}
```

### Configure Proxy Settings

After adding the required OkHttp dependency, you need to configure the proxy settings for your Ably client. This can be done by setting the proxy options in the `ClientOptions` object when you instantiate the Ably SDK.

Here’s an example of how to configure and use a proxy:

#### Java Example

```java
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ProxyOptions;
import io.ably.lib.http.HttpAuth;

public class AblyWithProxy {
    public static void main(String[] args) throws Exception {
        // Configure Ably Client options
        ClientOptions options = new ClientOptions();
        
        // Setup proxy settings
        ProxyOptions proxy = new ProxyOptions();
        proxy.host = "your-proxy-host";  // Replace with your proxy host
        proxy.port = 8080;               // Replace with your proxy port
        
        // Optional: If the proxy requires authentication
        proxy.username = "your-username";  // Replace with proxy username
        proxy.password = "your-password";  // Replace with proxy password
        proxy.prefAuthType = HttpAuth.Type.BASIC;  // Choose your preferred authentication type (e.g., BASIC or DIGEST)

        // Attach the proxy settings to the client options
        options.proxy = proxy;

        // Create an instance of Ably using the configured options
        AblyRest ably = new AblyRest(options);

        // Alternatively, for real-time connections
        AblyRealtime ablyRealtime = new AblyRealtime(options);

        // Use the Ably client as usual
    }
}
```

## Resources

Visit https://www.ably.com/docs for a complete API reference and more examples.

### Example projects:

- [Ably Asset Tracking SDKs for Android](https://github.com/ably/ably-asset-tracking-android/blob/main/README.md#useful-resources)
- [Chat app using Spring Boot + Auth0 + Ably](https://github.com/ably-labs/spring-boot-auth0)
- [Spring + Ably Pub/Sub Demo with a Collaborative TODO list](https://github.com/ably-labs/ably-spring-pubsub)

## Requirements

For Java, JRE 8 or later is required. Note that the [Java Unlimited JCE extensions](https://www.oracle.com/uk/java/technologies/javase-jce8-downloads.html) must be installed in the Java runtime environment.

For Android, 4.4 KitKat (API level 19) or later is required.

## Support, feedback and troubleshooting

Please visit http://support.ably.io/ for access to our knowledgebase and to ask for any assistance.

You can also view the [community reported Github issues](https://github.com/ably/ably-java/issues).

To see what has changed in recent versions of Bundler, see the [CHANGELOG](CHANGELOG.md).

## Contributing

For guidance on how to contribute to this project, see [CONTRIBUTING.md](CONTRIBUTING.md).
