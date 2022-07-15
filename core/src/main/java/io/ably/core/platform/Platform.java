package io.ably.core.platform;

import io.ably.core.transport.NetworkConnectivity;

public interface Platform {
    NetworkConnectivity getNetworkConnectivity();
}
