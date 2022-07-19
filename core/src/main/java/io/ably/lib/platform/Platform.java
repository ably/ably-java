package io.ably.lib.platform;

import io.ably.lib.transport.NetworkConnectivity;

public interface Platform {
    NetworkConnectivity getNetworkConnectivity();
}
