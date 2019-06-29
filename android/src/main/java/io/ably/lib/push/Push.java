package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;

public class Push extends PushBase {

	public Push(AblyBase rest) {
		super(rest);
		String clientId = rest.auth.clientId;
		if(clientId != null) {
			try {
				getActivationContext().setClientId(clientId);
			} catch(AblyException ae) {}
		}
	}

	public void activate() throws AblyException {
		activate(false);
	}

	public void activate(boolean useCustomRegistrar) throws AblyException {
		Log.v(TAG, "activate(): useCustomRegistrar " + useCustomRegistrar);
		Context context = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		getStateMachine().handleEvent(ActivationStateMachine.CalledActivate.useCustomRegistrar(useCustomRegistrar, prefs));
	}

	public void deactivate() throws AblyException {
		deactivate(false);
	}

	public void deactivate(boolean useCustomRegistrar) throws AblyException {
		Log.v(TAG, "deactivate(): useCustomRegistrar " + useCustomRegistrar);
		Context context = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		getStateMachine().handleEvent(ActivationStateMachine.CalledDeactivate.useCustomRegistrar(useCustomRegistrar, prefs));
	}

	synchronized ActivationStateMachine getStateMachine() throws AblyException {
		return getActivationContext().getActivationStateMachine();
	}

	Context getApplicationContext() throws AblyException {
		Context applicationContext = rest.platform.getApplicationContext();
		if(applicationContext == null) {
			Log.e(TAG, "getApplicationContext(): Unable to get application context; not set");
			throw AblyException.fromErrorInfo(new ErrorInfo("Unable to get application context; not set", 40000, 400));
		}
		return applicationContext;
	}

	public ActivationContext getActivationContext() throws AblyException {
		Context applicationContext = getApplicationContext();
		return ActivationContext.getActivationContext(applicationContext, (AblyRest)rest);
	}

	public LocalDevice getLocalDevice() throws AblyException {
		return getActivationContext().getLocalDevice();
	}

	private static final String TAG = Push.class.getName();
}
