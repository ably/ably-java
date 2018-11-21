package io.ably.lib.platform;

import android.content.Context;
import io.ably.lib.transport.NetworkConnectivity;

public class Platform {
	public static final String name = "android";

	public Platform() {
		networkConnectivity = new AndroidNetworkConnectivity();
	}
	/**
	 * Get the Android Context for this instance
	 * @return
	 */
	public Context getAndroidContext() { return context; }

	/**
	 * Set the Android Context for this instance, replacing any existing context
	 */
	public void setAndroidContext(Context context) {
		clearAndroidContext();
		this.context = context;
		networkConnectivity.activate(context);
	}

	/**
	 * Clear the Android Context for this instance
	 */
	public void clearAndroidContext() {
		if(context != null) {
			networkConnectivity.deactivate(context);
			context = null;
		}
	}

	public NetworkConnectivity getNetworkConnectvity() {
		return networkConnectivity;
	}

	private Context context;
	private AndroidNetworkConnectivity networkConnectivity;
}
