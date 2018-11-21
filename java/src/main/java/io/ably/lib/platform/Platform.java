package io.ably.lib.platform;

import io.ably.lib.rest.AblyBase;
import io.ably.lib.transport.NetworkConnectivity;

public class Platform {
	public static final String name = "java";

	public Platform(AblyBase ably) {
	}

	public NetworkConnectivity getNetworkConnectivity() {
		if(networkConnectivity != null) {
			return networkConnectivity;
		} else {
			return (networkConnectivity = new NetworkConnectivity.DefaultNetworkConnectivity());
		}
	}

	private NetworkConnectivity networkConnectivity;
}
