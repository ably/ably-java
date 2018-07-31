package io.ably.lib.util;

public class JavaNetworkConnectivity extends NetworkConnectivity {
	public static JavaNetworkConnectivity getInstance() { return instance; }
	private static final JavaNetworkConnectivity instance = new JavaNetworkConnectivity();
}
