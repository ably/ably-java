package io.ably.lib.platform;

import io.ably.lib.transport.NetworkConnectivity;

/**
 * An internal class with platform specific logic.
 * Even though it's internal, its visibility modifier is public because it's referenced by two other packages in this module (one for REST, one for Realtime).
 */
// TODO - change visibility to private or package when possible
public class JavaPlatform implements Platform {
    public static final String name = "java";

    public JavaPlatform() {}

    public NetworkConnectivity getNetworkConnectivity() {
        return networkConnectivity;
    }

    private final NetworkConnectivity networkConnectivity = new NetworkConnectivity.DefaultNetworkConnectivity();
}
