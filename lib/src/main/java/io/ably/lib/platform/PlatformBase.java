package io.ably.lib.platform;

import io.ably.lib.transport.NetworkConnectivity;

public interface PlatformBase {
    NetworkConnectivity getNetworkConnectivity();
}
