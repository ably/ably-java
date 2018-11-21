package io.ably.lib.platform;

import io.ably.lib.transport.NetworkConnectivity;

public class Platform {
	public static final String name = "java";

	public NetworkConnectivity getNetworkConnectvity() {
		if(networkConnectivity != null) {
			return networkConnectivity;
		} else {
			return (networkConnectivity = new NetworkConnectivity.DefaultNetworkConnectivity());
		}
	}

	private NetworkConnectivity networkConnectivity;
}
