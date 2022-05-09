package io.ably.lib.platform;

import io.ably.lib.transport.NetworkConnectivity;

public class JavaPlatform implements PlatformBase {
    public static final String name = "java";

    public JavaPlatform() {}

    public NetworkConnectivity getNetworkConnectivity() {
        return networkConnectivity;
    }

    private final NetworkConnectivity networkConnectivity = new NetworkConnectivity.DefaultNetworkConnectivity();
}
