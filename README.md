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
