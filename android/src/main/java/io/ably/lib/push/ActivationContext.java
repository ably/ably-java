package io.ably.lib.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Log;

import java.util.WeakHashMap;

public class ActivationContext {
	public ActivationContext(Context context) {
		this.context = context;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	Context getContext() {
		return context;
	}
	SharedPreferences getPreferences() { return prefs; }

	public synchronized LocalDevice getLocalDevice() {
		if(localDevice == null) {

			localDevice = new LocalDevice(this);
		}
		return localDevice;
	}

	public synchronized void setActivationStateMachine(ActivationStateMachine activationStateMachine) {
		this.activationStateMachine = activationStateMachine;
	}

	public synchronized ActivationStateMachine getActivationStateMachine() {
		if(activationStateMachine == null) {
			activationStateMachine = new ActivationStateMachine(this);
		}
		return activationStateMachine;
	}

	public void setAbly(AblyRest ably) {
		this.ably = ably;
		this.clientId = ably.auth.clientId;
	}

	AblyRest getAbly() throws AblyException {
		if(ably != null) {
			Log.v(TAG, "getAbly(): returning existing Ably instance");
			return ably;
		}

		String deviceIdentityToken = getLocalDevice().deviceIdentityToken;
		if(deviceIdentityToken == null) {
			Log.e(TAG, "getAbly(): unable to create Ably instance using deviceIdentityToken");
			throw AblyException.fromErrorInfo(new ErrorInfo("Unable to get Ably library instance; no device identity token", 40000, 400));
		}
		Log.v(TAG, "getAbly(): returning Ably instance using deviceIdentityToken");
		return (ably = new AblyRest(deviceIdentityToken));
	}

	public boolean setClientId(String clientId) {
		boolean updated = !clientId.equals(this.clientId);
		if(updated) {
			if(localDevice != null) {
				localDevice.setClientId(clientId);
				if(localDevice.isRegistered()) {
					if(activationStateMachine != null) {
						activationStateMachine.handleEvent(new ActivationStateMachine.GotPushDeviceDetails());
					}
				}
			}
		}
		return updated;
	}

	public void onNewRegistrationToken(RegistrationToken.Type type, String token) {
		LocalDevice localDevice = getLocalDevice();
		RegistrationToken previous = localDevice.getRegistrationToken();
		if (previous != null) {
			if (previous.type != type) {
				Log.e(TAG, "trying to register device with " + type + ", but it was already registered with " + previous.type);
				return;
			}
			if (previous.token.equals(token)) {
				return;
			}
		}
		Log.v(TAG, "onNewRegistrationToken(): updating token");
		localDevice.setAndPersistRegistrationToken(new RegistrationToken(type, token));
		getActivationStateMachine().handleEvent(new ActivationStateMachine.GotPushDeviceDetails());
	}

	public void reset() {
		ably = null;

		getActivationStateMachine().reset();
		activationStateMachine = null;

		getLocalDevice().reset();
		localDevice = null;
	}

	public static ActivationContext getActivationContext(Context applicationContext) {
		return getActivationContext(applicationContext, null);
	}

	public static ActivationContext getActivationContext(Context applicationContext, AblyRest ably) {
		ActivationContext activationContext;
		synchronized (activationContexts) {
			activationContext = activationContexts.get(applicationContext);
			if(activationContext == null) {
				Log.v(TAG, "getActivationContext(): creating new ActivationContext for this application");
				activationContexts.put(applicationContext, (activationContext = new ActivationContext(applicationContext)));
			}
			if(ably != null) {
				activationContext.setAbly(ably);
			}
		}
		return activationContext;
	}

	void getRegistrationToken(OnCompleteListener<InstanceIdResult> listener) {
		FirebaseInstanceId.getInstance().getInstanceId()
				.addOnCompleteListener(listener);
	}

	protected AblyRest ably;
	protected String clientId;
	protected ActivationStateMachine activationStateMachine;
	protected LocalDevice localDevice;
	protected final SharedPreferences prefs;
	protected final Context context;

	private static WeakHashMap<Context, ActivationContext> activationContexts = new WeakHashMap<Context, ActivationContext>();
	private static final String TAG = ActivationContext.class.getName();
}
