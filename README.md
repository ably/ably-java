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

## Proxy support

You can add proxy support to the Ably Java SDK by configuring `ProxyOptions` in your client setup, enabling connectivity through corporate firewalls and secured networks.

<details>
<summary>Proxy support setup details.</summary>

To enable proxy support for both REST and Realtime clients in the Ably SDK, use the OkHttp library to handle HTTP requests and WebSocket connections.

Add the following dependency to your `build.gradle` file:

```groovy
dependencies {
    runtimeOnly("io.ably:network-client-okhttp:1.2.53")
}
```

After adding the OkHttp dependency, enable proxy support by specifying proxy settings in the ClientOptions when initializing your Ably client.

The following example sets up a proxy using the Pub/Sub Java SDK:

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

</details>  

## Support, feedback and troubleshooting

Please visit http://support.ably.io/ for access to our knowledgebase and to ask for any assistance.

You can also view the [community reported Github issues](https://github.com/ably/ably-java/issues).

To see what has changed in recent versions of Bundler, see the [CHANGELOG](CHANGELOG.md).


## Contribute

Read the [CONTRIBUTING.md](./CONTRIBUTING.md) guidelines to contribute to Ably.

---

## Releases

The [CHANGELOG.md](/ably/ably-js/blob/main/CHANGELOG.md) contains details of the latest releases for this SDK. You can also view all Ably releases on [changelog.ably.com](https://changelog.ably.com).

---
