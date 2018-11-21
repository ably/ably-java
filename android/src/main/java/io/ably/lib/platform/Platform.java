package io.ably.lib.platform;

import android.content.Context;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.transport.NetworkConnectivity;

import java.util.WeakHashMap;

public class Platform {
	public static final String name = "android";

	public Platform(AblyBase ably) {
		this.ably = ably;
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
		if(this.context != context) {
			clearAndroidContext();
			this.context = context;
			networkConnectivity.activate(context);
			ablyInstanceByContext.put(context, ably);
		}
	}

	/**
	 * Clear the Android Context for this instance
	 */
	public void clearAndroidContext() {
		if(context != null) {
			networkConnectivity.deactivate(context);
			context = null;
			ablyInstanceByContext.remove(context);
		}
	}

	public NetworkConnectivity getNetworkConnectivity() {
		return networkConnectivity;
	}

	public static AblyBase getAblyForContext(Context context) {
		return ablyInstanceByContext.get(context);
	}

	private Context context;
	private final AblyBase ably;
	private final AndroidNetworkConnectivity networkConnectivity;
	private static WeakHashMap<Context, AblyBase> ablyInstanceByContext = new WeakHashMap<Context, AblyBase>();
}
