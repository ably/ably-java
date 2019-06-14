package io.ably.lib.platform;

import android.content.Context;
import io.ably.lib.push.ActivationContext;
import io.ably.lib.push.ActivationStateMachine;
import io.ably.lib.push.Push;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.push.LocalDevice;
import io.ably.lib.transport.NetworkConnectivity;
import io.ably.lib.transport.NetworkConnectivity.DelegatedNetworkConnectivity;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;

import java.util.WeakHashMap;

public class Platform {
	public static final String name = "android";

	public Platform() {}

	public Context getApplicationContext() {
		return applicationContext;
	}
	/**
	 * Set the Android Context for this instance
	 */
	public void setAndroidContext(Context context) throws AblyException {
		context = context.getApplicationContext();
		if(applicationContext != null) {
			if(context == applicationContext) {
				return;
			}
			throw AblyException.fromErrorInfo(new ErrorInfo("Incompatible application context set", 40000, 400));
		}
		applicationContext = context;
		AndroidNetworkConnectivity.getNetworkConnectivity(context).addListener(this.networkConnectivity);
	}

	/**
	 * Get the NetworkConnectivity tracker instance for this context
	 * @return
	 */
	public NetworkConnectivity getNetworkConnectivity() {
		return networkConnectivity;
	}

	private Context applicationContext;
	private final DelegatedNetworkConnectivity networkConnectivity = new DelegatedNetworkConnectivity();
}
