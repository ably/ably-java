package io.ably.lib.rest;

import android.content.Context;
import io.ably.lib.push.LocalDevice;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.Log;

public class AblyRest extends AblyBase {
	/**
	 * Instance the Ably library using a key only.
	 * This is simply a convenience constructor for the
	 * simplest case of instancing the library with a key
	 * for basic authentication and no other options.
	 * @param key; String key (obtained from application dashboard)
	 * @throws AblyException
	 */
	public AblyRest(String key) throws AblyException {
		super(key);
	}

	/**
	 * Instance the Ably library with the given options.
	 * @param options: see {@link io.ably.lib.types.ClientOptions} for options
	 * @throws AblyException
	 */
	public AblyRest(ClientOptions options) throws AblyException {
		super(options);
	}

	/**
	 * Get the local device, if any
	 * @return an instance of LocalDevice, or null if this device is not capable of activation as a push target
	 * @throws AblyException
	 */
	public LocalDevice device() throws AblyException {
		return this.push.getLocalDevice();
	}

	/**
	 * Set the Android Context for this instance
	 */
	public void setAndroidContext(Context context) throws AblyException { this.platform.setAndroidContext(context); }

	/**
	 * clientId set by late initialisation
	 */
	protected void onClientIdSet(String clientId) {
		/* we only need to propagate any update to clientId if this is a late init */
		if(push != null && platform.hasApplicationContext()) {
			try {
				push.getActivationContext().setClientId(clientId);
			} catch(AblyException ae) {
				Log.e(TAG, "unable to update local device state");
			}
		}
	}

	private static final String TAG = AblyRest.class.getName();
}
