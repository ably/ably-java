package io.ably.lib.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.JsonObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;

import com.google.gson.JsonPrimitive;
import io.ably.lib.http.*;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.*;
import io.ably.lib.util.IntentUtils;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

public class ActivationStateMachine {
	public static class CalledActivate extends ActivationStateMachine.Event {
		public static ActivationStateMachine.CalledActivate useCustomRegistrar(boolean useCustomRegistrar, SharedPreferences prefs) {
			prefs.edit().putBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, useCustomRegistrar).apply();
			return new ActivationStateMachine.CalledActivate();
		}
	}

	public static class CalledDeactivate extends ActivationStateMachine.Event {
		static ActivationStateMachine.CalledDeactivate useCustomRegistrar(boolean useCustomRegistrar, SharedPreferences prefs) {
			prefs.edit().putBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, useCustomRegistrar).apply();
			return new ActivationStateMachine.CalledDeactivate();
		}
	}

	public static class GotPushDeviceDetails extends ActivationStateMachine.Event {}

	public static class GotDeviceRegistration extends ActivationStateMachine.Event {
		final String deviceIdentityToken;
		GotDeviceRegistration(String token) { this.deviceIdentityToken = token; }
	}

	public static class GettingDeviceRegistrationFailed extends ActivationStateMachine.ErrorEvent {
		GettingDeviceRegistrationFailed(ErrorInfo reason) { super(reason); }
	}

	public static class GettingPushDeviceDetailsFailed extends ActivationStateMachine.ErrorEvent {
		GettingPushDeviceDetailsFailed(ErrorInfo reason) { super(reason); }
	}

	public static class RegistrationSynced extends ActivationStateMachine.Event {}

	public static class SyncRegistrationFailed extends ActivationStateMachine.ErrorEvent {
		public SyncRegistrationFailed(ErrorInfo reason) { super(reason); }
	}

	public static class Deregistered extends ActivationStateMachine.Event {}

	public static class DeregistrationFailed extends ActivationStateMachine.ErrorEvent {
		public DeregistrationFailed(ErrorInfo reason) { super(reason); }
	}

	public abstract static class Event {}

	abstract static class ErrorEvent extends ActivationStateMachine.Event {
		final ErrorInfo reason;
		ErrorEvent(ErrorInfo reason) { this.reason = reason; }
	}

	public static class NotActivated extends ActivationStateMachine.PersistentState {
		public NotActivated(ActivationStateMachine machine) { super(machine); }
		public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledDeactivate) {
				machine.callDeactivatedCallback(null);
				return this;
			} else if (event instanceof ActivationStateMachine.CalledActivate) {
				LocalDevice device = machine.getDevice();

				if (device.isRegistered()) {
					machine.pendingEvents.add(new ActivationStateMachine.CalledActivate());
					machine.validateRegistration();
					return new ActivationStateMachine.WaitingForRegistrationSync(machine);
				}

				if (device.getRegistrationToken() != null) {
					machine.pendingEvents.add(new ActivationStateMachine.GotPushDeviceDetails());
				} else {
					machine.getRegistrationToken();
				}

				if(!device.isCreated()) {
					device.create();
				}

				return new ActivationStateMachine.WaitingForPushDeviceDetails(machine);
			} else if (event instanceof ActivationStateMachine.GotPushDeviceDetails) {
				return this;
			}
			return null;
		}
	}

	public static class WaitingForPushDeviceDetails extends ActivationStateMachine.PersistentState {
		public WaitingForPushDeviceDetails(ActivationStateMachine machine) { super(machine); }
		public ActivationStateMachine.State transition(final ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledActivate) {
				return this;
			} else if (event instanceof ActivationStateMachine.CalledDeactivate) {
				machine.callDeactivatedCallback(null);
				return new ActivationStateMachine.NotActivated(machine);
			} else if (event instanceof ActivationStateMachine.GettingPushDeviceDetailsFailed) {
				machine.callDeactivatedCallback(((ActivationStateMachine.GettingPushDeviceDetailsFailed)event).reason);
				return new ActivationStateMachine.NotActivated(machine);
			} else if (event instanceof ActivationStateMachine.GotPushDeviceDetails) {
				final ActivationContext activationContext = machine.activationContext;
				final LocalDevice device = activationContext.getLocalDevice();

				boolean useCustomRegistrar = activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false);
				if (useCustomRegistrar) {
					machine.invokeCustomRegistration(device, true);
				} else {
					final AblyRest ably;
					try {
						ably = activationContext.getAbly();
					} catch(AblyException ae) {
						ErrorInfo reason = ae.errorInfo;
						Log.e(TAG, "exception registering " + device.id + ": " + reason.toString());
						machine.handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(reason));
						return new ActivationStateMachine.NotActivated(machine);
					}
					final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.toJsonObject(), ably.options.useBinaryProtocol);
					ably.http.request(new Http.Execute<JsonObject>() {
						@Override
						public void execute(HttpScheduler http, Callback<JsonObject> callback) throws AblyException {
							Param[] params = null;
							if(ably.options.pushFullWait) {
								params = Param.push(null, "fullWait", "true");
							}
							/* this is authenticated using the Ably library credentials, plus the deviceSecret in the request body */
							http.post("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, body, new Serialisation.HttpResponseHandler<JsonObject>(), true, callback);
						}
					}).async(new Callback<JsonObject>() {
						@Override
						public void onSuccess(JsonObject response) {
							Log.i(TAG, "registered " + device.id);
							JsonObject deviceIdentityTokenJson = response.getAsJsonObject("deviceIdentityToken");
							if(deviceIdentityTokenJson == null) {
								Log.e(TAG, "invalid device registration response (no deviceIdentityToken); deviceId = " + device.id);
								machine.handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(new ErrorInfo("Invalid deviceIdentityToken in response", 40000, 400)));
								return;
							}
							JsonPrimitive responseClientIdJson = response.getAsJsonPrimitive("clientId");
							if(responseClientIdJson != null) {
								String responseClientId = responseClientIdJson.getAsString();
								if(device.clientId == null) {
									/* Spec RSH8f: there is an implied clientId in our credentials that we didn't know about */
									activationContext.setClientId(responseClientId, false);
								}
							}
							machine.handleEvent(new ActivationStateMachine.GotDeviceRegistration(deviceIdentityTokenJson.getAsJsonPrimitive("token").getAsString()));
						}
						@Override
						public void onError(ErrorInfo reason) {
							Log.e(TAG, "error registering " + device.id + ": " + reason.toString());
							machine.handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(reason));
						}
					});
				}

				return new ActivationStateMachine.WaitingForDeviceRegistration(machine);
			}
			return null;
		}
	}

	public static class WaitingForDeviceRegistration extends ActivationStateMachine.State {
		public WaitingForDeviceRegistration(ActivationStateMachine machine) { super(machine); }
		public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledActivate) {
				return this;
			} else if (event instanceof ActivationStateMachine.GotDeviceRegistration) {
				LocalDevice device = machine.getDevice();
				device.setDeviceIdentityToken(((ActivationStateMachine.GotDeviceRegistration) event).deviceIdentityToken);
				machine.callActivatedCallback(null);
				return new ActivationStateMachine.WaitingForNewPushDeviceDetails(machine);
			} else if (event instanceof ActivationStateMachine.GettingDeviceRegistrationFailed) {
				machine.callActivatedCallback(((ActivationStateMachine.GettingDeviceRegistrationFailed) event).reason);
				return new ActivationStateMachine.NotActivated(machine);
			}
			return null;
		}
	}

	public static class WaitingForNewPushDeviceDetails extends ActivationStateMachine.PersistentState {
		public WaitingForNewPushDeviceDetails(ActivationStateMachine machine) { super(machine); }
		public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledActivate) {
				machine.callActivatedCallback(null);
				return this;
			} else if (event instanceof ActivationStateMachine.CalledDeactivate) {
				LocalDevice device = machine.getDevice();
				machine.deregister();
				return new ActivationStateMachine.WaitingForDeregistration(machine, this);
			} else if (event instanceof ActivationStateMachine.GotPushDeviceDetails) {
				machine.getDevice();
				machine.updateRegistration();

				return new WaitingForRegistrationSync(machine);
			}
			return null;
		}
	}

	public static class WaitingForRegistrationSync extends ActivationStateMachine.State {
		public WaitingForRegistrationSync(ActivationStateMachine machine) { super(machine); }
		public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledActivate) {
				machine.callActivatedCallback(null);
				return this;
			} else if (event instanceof RegistrationSynced) {
				return new ActivationStateMachine.WaitingForNewPushDeviceDetails(machine);
			} else if (event instanceof SyncRegistrationFailed) {
				// TODO: Here we could try to recover ourselves if the error is e. g.
				// a networking error. Just notify the user for now.
				machine.callSyncRegistrationFailedCallback(((SyncRegistrationFailed) event).reason);
				return new AfterRegistrationSyncFailed(machine);
			}
			return null;
		}
	}

	public static class AfterRegistrationSyncFailed extends ActivationStateMachine.PersistentState {
		public AfterRegistrationSyncFailed(ActivationStateMachine machine) { super(machine); }
		public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledActivate || event instanceof ActivationStateMachine.GotPushDeviceDetails) {
				machine.validateRegistration();
				return new WaitingForRegistrationSync(machine);
			} else if (event instanceof ActivationStateMachine.CalledDeactivate) {
				machine.deregister();
				return new ActivationStateMachine.WaitingForDeregistration(machine, this);
			}
			return null;
		}
	}

	public static class WaitingForDeregistration extends ActivationStateMachine.State {
		private ActivationStateMachine.State previousState;

		public WaitingForDeregistration(ActivationStateMachine machine, ActivationStateMachine.State previousState) {
			super(machine);
			this.previousState = previousState;
		}

		public ActivationStateMachine.State transition(ActivationStateMachine.Event event) {
			if (event instanceof ActivationStateMachine.CalledDeactivate) {
				return this;
			} else if (event instanceof ActivationStateMachine.Deregistered) {
				LocalDevice device = machine.getDevice();
				device.reset();
				machine.callDeactivatedCallback(null);
				return new ActivationStateMachine.NotActivated(machine);
			} else if (event instanceof ActivationStateMachine.DeregistrationFailed) {
				machine.callDeactivatedCallback(((ActivationStateMachine.DeregistrationFailed) event).reason);
				return previousState;
			}
			return null;
		}
	}

	private LocalDevice getDevice() {
		return activationContext.getLocalDevice();
	}

	public static abstract class State {
		protected final ActivationStateMachine machine;

		public State(ActivationStateMachine machine) {
			this.machine = machine;
		}

		public abstract ActivationStateMachine.State transition(ActivationStateMachine.Event event);
	}

	private static abstract class PersistentState extends ActivationStateMachine.State {
		PersistentState(ActivationStateMachine machine) { super(machine); }
	}

	private void callActivatedCallback(ErrorInfo reason) {
		sendErrorIntent("PUSH_ACTIVATE", reason);
	}

	private void callDeactivatedCallback(ErrorInfo reason) {
		sendErrorIntent("PUSH_DEACTIVATE", reason);
	}

	private void callSyncRegistrationFailedCallback(ErrorInfo reason) {
		sendErrorIntent("PUSH_UPDATE_FAILED", reason);
	}

	private void sendErrorIntent(String name, ErrorInfo error) {
		Intent intent = new Intent();
		IntentUtils.addErrorInfo(intent, error);
		sendIntent(name, intent);
	}

	private void invokeCustomRegistration(final DeviceDetails device, final boolean isNew) {
		registerOnceReceiver("PUSH_DEVICE_REGISTERED", new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				ErrorInfo error = IntentUtils.getErrorInfo(intent);
				if (error == null) {
					Log.i(TAG, "custom registration for " + device.id);
					if (isNew) {
						handleEvent(new ActivationStateMachine.GotDeviceRegistration(intent.getStringExtra("deviceIdentityToken")));
					} else {
						handleEvent(new RegistrationSynced());
					}
				} else {
					Log.e(TAG, "error from custom registration for " + device.id + ": " + error.toString());
					if (isNew) {
						handleEvent(new ActivationStateMachine.GettingDeviceRegistrationFailed(error));
					} else {
						handleEvent(new SyncRegistrationFailed(error));
					}
				}
			}
		});

		Intent intent = new Intent();
		intent.putExtra("isNew", isNew);
		sendIntent("PUSH_REGISTER_DEVICE", intent);
	}

	private void invokeCustomDeregistration(final DeviceDetails device) {
		registerOnceReceiver("PUSH_DEVICE_DEREGISTERED", new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				ErrorInfo error = IntentUtils.getErrorInfo(intent);
				if (error == null) {
					Log.i(TAG, "custom deregistration for " + device.id);
					handleEvent(new ActivationStateMachine.Deregistered());
				} else {
					Log.e(TAG, "error from custom deregisterer for " + device.id + ": " + error.toString());
					handleEvent(new ActivationStateMachine.DeregistrationFailed(error));
				}
			}
		});

		Intent intent = new Intent();
		sendIntent("PUSH_DEREGISTER_DEVICE", intent);
	}

	private void sendIntent(String name, Intent intent) {
		intent.setAction("io.ably.broadcast." + name);
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}

	private void registerOnceReceiver(String name, final BroadcastReceiver receiver) {
		BroadcastReceiver onceReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(this);
				receiver.onReceive(context, intent);
			}
		};
		IntentFilter filter = new IntentFilter("io.ably.broadcast." + name);
		LocalBroadcastManager.getInstance(context).registerReceiver(onceReceiver, filter);
	}

	protected void getRegistrationToken() {
		activationContext.getRegistrationToken(new OnCompleteListener<InstanceIdResult>() {
			@Override
			public void onComplete(Task<InstanceIdResult> task) {
				if(task.isSuccessful()) {
					/* Get new Instance ID token */
					String token = task.getResult().getToken();
					Log.i(TAG, "getInstanceId completed with new token");
					activationContext.onNewRegistrationToken(RegistrationToken.Type.FCM, token);
				} else {
					Log.e(TAG, "getInstanceId failed", task.getException());
					handleEvent(new ActivationStateMachine.GettingPushDeviceDetailsFailed(ErrorInfo.fromThrowable(task.getException())));
				}
			}
		});
	}

	private void updateRegistration() {
		final LocalDevice device = activationContext.getLocalDevice();
		boolean useCustomRegistrar = activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false);
		if (useCustomRegistrar) {
			invokeCustomRegistration(device, false);
		} else {
			final AblyRest ably;
			try {
				ably = activationContext.getAbly();
			} catch(AblyException ae) {
				ErrorInfo reason = ae.errorInfo;
				Log.e(TAG, "exception registering " + device.id + ": " + reason.toString());
				handleEvent(new SyncRegistrationFailed(reason));
				return;
			}
			final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.pushRecipientJsonObject(), ably.options.useBinaryProtocol);
			ably.http.request(new Http.Execute<Void>() {
				@Override
				public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
					Param[] params = null;
					if (ably.options.pushFullWait) {
						params = Param.push(params, "fullWait", "true");
					}

					http.patch("/push/deviceRegistrations/" + device.id, ably.push.pushRequestHeaders(true), params, body, null, false, callback);
				}
			}).async(new Callback<Void>() {
				@Override
				public void onSuccess(Void response) {
					Log.i(TAG, "updated registration " + device.id);
					handleEvent(new RegistrationSynced());
				}
				@Override
				public void onError(ErrorInfo reason) {
					Log.e(TAG, "error updating registration " + device.id + ": " + reason.toString());
					handleEvent(new SyncRegistrationFailed(reason));
				}
			});
		}
	}

	private void validateRegistration() {
		final LocalDevice device = activationContext.getLocalDevice();
		final AblyRest ably;
		try {
			ably = activationContext.getAbly();
		} catch(AblyException ae) {
			ErrorInfo reason = ae.errorInfo;
			Log.e(TAG, "exception validating registration for " + device.id + ": " + reason.toString());
			handleEvent(new SyncRegistrationFailed(reason));
			return;
		}
		/* Spec: RSH3a2a1, RSH8g: verify that the existing registration is compatible with the present credentials */
		String presentClientId = ably.auth.clientId;
		if(presentClientId != null && device.clientId != null && !presentClientId.equals(device.clientId)) {
			ErrorInfo clientIdErr = new ErrorInfo("Activation failed: present clientId is not compatible with existing device registration", 400, 61002);
			handleEvent(new SyncRegistrationFailed(clientIdErr));
			return;
		}

		boolean useCustomRegistrar = activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false);
		if (useCustomRegistrar) {
			invokeCustomRegistration(device, false);
		} else {
			ably.http.request(new Http.Execute<JsonObject>() {
				@Override
				public void execute(HttpScheduler http, Callback<JsonObject> callback) throws AblyException {
					Param[] params = null;
					if (ably.options.pushFullWait) {
						params = Param.push(params, "fullWait", "true");
					}

					final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.toJsonObject(), ably.options.useBinaryProtocol);
					http.put("/push/deviceRegistrations/" + device.id, ably.push.pushRequestHeaders(true), params, body, new Serialisation.HttpResponseHandler<JsonObject>(), false, callback);
				}
			}).async(new Callback<JsonObject>() {
				@Override
				public void onSuccess(JsonObject response) {
					Log.i(TAG, "updated registration " + device.id);
					JsonPrimitive responseClientIdJson = response.getAsJsonPrimitive("clientId");
					if(responseClientIdJson != null) {
						String responseClientId = responseClientIdJson.getAsString();
						if(device.clientId == null) {
							/* Spec RSH8f: there is an implied clientId in our credentials that we didn't know about */
							activationContext.setClientId(responseClientId, false);
						}
					}
					handleEvent(new RegistrationSynced());
				}
				@Override
				public void onError(ErrorInfo reason) {
					Log.e(TAG, "error validating registration " + device.id + ": " + reason.toString());
					handleEvent(new SyncRegistrationFailed(reason));
				}
			});
		}
	}

	private void deregister() {
		final LocalDevice device = activationContext.getLocalDevice();
		if (activationContext.getPreferences().getBoolean(ActivationStateMachine.PersistKeys.PUSH_CUSTOM_REGISTRAR, false)) {
			invokeCustomDeregistration(device);
		} else {
			final AblyRest ably;
			try {
				ably = activationContext.getAbly();
			} catch(AblyException ae) {
				ErrorInfo reason = ae.errorInfo;
				Log.e(TAG, "exception registering " + device.id + ": " + reason.toString());
				handleEvent(new ActivationStateMachine.DeregistrationFailed(reason));
				return;
			}
			ably.http.request(new Http.Execute<Void>() {
				@Override
				public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
					Param[] params = new Param[0];
					if (ably.options.pushFullWait) {
						params = Param.push(params, "fullWait", "true");
					}
					http.del("/push/deviceRegistrations/" + device.id, ably.push.pushRequestHeaders(true), params, null, true, callback);
				}
			}).async(new Callback<Void>() {
				@Override
				public void onSuccess(Void response) {
					Log.i(TAG, "deregistered " + device.id);
					handleEvent(new ActivationStateMachine.Deregistered());
				}
				@Override
				public void onError(ErrorInfo reason) {
					Log.e(TAG, "error deregistering " + device.id + ": " + reason.toString());
					handleEvent(new ActivationStateMachine.DeregistrationFailed(reason));
				}
			});
		}
	}

	protected final ActivationContext activationContext;
	private final Context context;
	public ActivationStateMachine.State current;
	public ArrayDeque<ActivationStateMachine.Event> pendingEvents;

	public ActivationStateMachine(ActivationContext activationContext) {
		this.activationContext = activationContext;
		this.context = activationContext.getContext();
		current = getPersistedState();
		pendingEvents = getPersistedPendingEvents();
	}

	public synchronized boolean handleEvent(ActivationStateMachine.Event event) {
		Log.d(TAG, String.format("handling event %s from %s", event.getClass().getSimpleName(), current.getClass().getSimpleName()));

		ActivationStateMachine.State maybeNext = current.transition(event);
		if (maybeNext == null) {
			Log.d(TAG, "enqueuing event: " + event.getClass().getSimpleName());
			pendingEvents.add(event);
			return true;
		}

		Log.d(TAG, String.format("transition: %s -(%s)-> %s", current.getClass().getSimpleName(), event.getClass().getSimpleName(), maybeNext.getClass().getSimpleName()));
		current = maybeNext;

		while (true) {
			ActivationStateMachine.Event pending = pendingEvents.peek();
			if (pending == null) {
				break;
			}

			Log.d(TAG, "attempting to consume pending event: " + pending.getClass().getSimpleName());

			maybeNext = current.transition(pending);
			if (maybeNext == null) {
				break;
			}
			pendingEvents.poll();

			Log.d(TAG, String.format("transition: %s -(%s)-> %s", current.getClass().getSimpleName(), pending.getClass().getSimpleName(), maybeNext.getClass().getSimpleName()));
			current = maybeNext;
		}

		return persist();
	}

	public boolean reset() {
		SharedPreferences.Editor editor = activationContext.getPreferences().edit();
		for (Field f : ActivationStateMachine.PersistKeys.class.getDeclaredFields()) {
				try {
					editor.remove((String) f.get(null));
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
		}
		return editor.commit();
	}

	private boolean persist() {
		SharedPreferences.Editor editor = activationContext.getPreferences().edit();

		if (current instanceof ActivationStateMachine.PersistentState) {
			editor.putString(ActivationStateMachine.PersistKeys.CURRENT_STATE, current.getClass().getName());
		}

		editor.putInt(ActivationStateMachine.PersistKeys.PENDING_EVENTS_LENGTH, pendingEvents.size());
		int i = 0;
		for (ActivationStateMachine.Event e : pendingEvents) {
			editor.putString(
					String.format("%s[%d]", ActivationStateMachine.PersistKeys.PENDING_EVENTS_PREFIX, i),
					e.getClass().getName()
			);

			i++;
		}

		return editor.commit();
	}

	private ActivationStateMachine.State getPersistedState() {
		try {
			Class<ActivationStateMachine.State> stateClass = (Class<ActivationStateMachine.State>) Class.forName(activationContext.getPreferences().getString(ActivationStateMachine.PersistKeys.CURRENT_STATE, ""));
			Constructor<ActivationStateMachine.State> constructor = stateClass.getConstructor(this.getClass());
			return constructor.newInstance(this);
		} catch (Exception e) {
			return new ActivationStateMachine.NotActivated(this);
		}
	}

	private ArrayDeque<ActivationStateMachine.Event> getPersistedPendingEvents() {
		int length = activationContext.getPreferences().getInt(ActivationStateMachine.PersistKeys.PENDING_EVENTS_LENGTH, 0);
		ArrayDeque<ActivationStateMachine.Event> deque = new ArrayDeque<>(length);
		for (int i = 0; i < length; i++) {
			try {
				String className = activationContext.getPreferences().getString(String.format("%s[%d]", ActivationStateMachine.PersistKeys.PENDING_EVENTS_PREFIX, i), "");
				ActivationStateMachine.Event event = ((Class<ActivationStateMachine.Event>) Class.forName(className)).newInstance();
				deque.add(event);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
		return deque;
	}

	private static class PersistKeys {
		static final String CURRENT_STATE = "ABLY_PUSH_CURRENT_STATE";
		static final String PENDING_EVENTS_LENGTH = "ABLY_PUSH_PENDING_EVENTS_LENGTH";
		static final String PENDING_EVENTS_PREFIX = "ABLY_PUSH_PENDING_EVENTS";
		static final String PUSH_CUSTOM_REGISTRAR = "ABLY_PUSH_REGISTRATION_HANDLER";
	}

	private static final String TAG = "AblyActivation";
}
