package io.ably.lib.util;

public class Platform {
	public static final String name = "android";

	public static NetworkConnectivity getNetworkConnectvity() {
		return AndroidNetworkConnectivity.getInstance();
	}
}
