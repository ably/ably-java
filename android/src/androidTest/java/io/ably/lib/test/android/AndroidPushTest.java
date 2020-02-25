package io.ably.lib.test.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.test.AndroidTestCase;

import android.util.Log;
import com.google.gson.JsonObject;
import io.ably.lib.push.*;
import io.ably.lib.push.ActivationStateMachine.AfterRegistrationSyncFailed;
import io.ably.lib.push.ActivationStateMachine.CalledActivate;
import io.ably.lib.push.ActivationStateMachine.CalledDeactivate;
import io.ably.lib.push.ActivationStateMachine.Deregistered;
import io.ably.lib.push.ActivationStateMachine.DeregistrationFailed;
import io.ably.lib.push.ActivationStateMachine.Event;
import io.ably.lib.push.ActivationStateMachine.GettingDeviceRegistrationFailed;
import io.ably.lib.push.ActivationStateMachine.GotDeviceRegistration;
import io.ably.lib.push.ActivationStateMachine.GotPushDeviceDetails;
import io.ably.lib.push.ActivationStateMachine.NotActivated;
import io.ably.lib.push.ActivationStateMachine.RegistrationSynced;
import io.ably.lib.push.ActivationStateMachine.State;
import io.ably.lib.push.ActivationStateMachine.WaitingForDeregistration;
import io.ably.lib.push.ActivationStateMachine.WaitingForDeviceRegistration;
import io.ably.lib.push.ActivationStateMachine.WaitingForNewPushDeviceDetails;
import io.ably.lib.push.ActivationStateMachine.WaitingForPushDeviceDetails;
import io.ably.lib.push.ActivationStateMachine.WaitingForRegistrationSync;
import io.ably.lib.push.ActivationStateMachine.SyncRegistrationFailed;
import io.ably.lib.rest.DeviceDetails;
import io.ably.lib.types.*;
import io.ably.lib.util.Base64Coder;
import io.azam.ulidj.ULID;
import junit.extensions.TestSetup;
import junit.framework.TestSuite;

import junit.framework.Test;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.AsyncWaiter;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.util.TestCases;
import io.ably.lib.util.IntentUtils;
import io.ably.lib.util.JsonUtils;
import io.ably.lib.util.Serialisation;

import static io.ably.lib.test.common.Helpers.assertArrayUnorderedEquals;
import static io.ably.lib.test.common.Helpers.assertInstanceOf;
import static io.ably.lib.test.common.Helpers.assertSize;

public class AndroidPushTest extends AndroidTestCase {

	private class TestActivation {
		private Helpers.RawHttpTracker httpTracker;
		private AblyRest rest;
		private TestActivationContext activationContext;
		private TestActivationStateMachine machine;
		private AblyRest adminRest;

		TestActivation() {
			this(null);
		}

		TestActivation(Helpers.AblyFunction<ClientOptions, Void> configure) {
			this(configure, true);
		}

		TestActivation(boolean clearPersisted) {
			this(null, clearPersisted);
		}

		TestActivation(Helpers.AblyFunction<ClientOptions, Void> configure, boolean clearPersisted) {
			try {
				httpTracker = new Helpers.RawHttpTracker();
				DebugOptions options = createOptions(testVars.keys[0].keyStr);
				options.httpListener = httpTracker;
				options.useTokenAuth = true;
				if (configure != null) {
					configure.apply(options);
				}
				rest = new AblyRest(options);
				rest.auth.authorize(null, null);
				Context context = getContext();
				rest.setAndroidContext(context);
				activationContext = new TestActivationContext(context.getApplicationContext());
				activationContext.setAbly(rest);
				ActivationContext.setActivationContext(context.getApplicationContext(), activationContext);
				if (clearPersisted) {
					activationContext.reset();
				}
				machine = new TestActivationStateMachine(activationContext);
				activationContext.setActivationStateMachine(machine);

				adminRest = new AblyRest(options);
				adminRest.auth.authorize(new Auth.TokenParams() {{
					clientId = Auth.WILDCARD_CLIENTID;
				}}, null);
			} catch(AblyException e) {}
		}

		private void registerAndWait() throws AblyException {
			AsyncWaiter<Helpers.RawHttpRequest> requestWaiter = httpTracker.getRequestWaiter();
			AsyncWaiter<Intent> activateWaiter = broadcastWaiter("PUSH_ACTIVATE");

			rest.push.getActivationContext().onNewRegistrationToken(RegistrationToken.Type.FCM, "testToken");
			rest.push.activate(false);

			activateWaiter.waitFor();
			assertNull(activateWaiter.error);
			requestWaiter.waitFor();
			Helpers.RawHttpRequest request = requestWaiter.result;
			Log.d("AndroidPushTest"," registration method: " + request.method);
			assertTrue(request.method.equals("PATCH") || request.method.equals("POST"));
			assertTrue(request.url.getPath().startsWith("/push/deviceRegistrations"));
		}

		private void moveToAfterRegistrationUpdateFailed() throws AblyException {
			// Move to AfterRegistrationSyncFailed by forcing an update failure.

			rest.push.activate(true); // Just to set useCustomRegistrar to true.
			AsyncWaiter<Intent> customRegisterer = broadcastWaiter("PUSH_REGISTER_DEVICE");
			rest.push.getActivationContext().onNewRegistrationToken(RegistrationToken.Type.FCM, "testTokenFailed");
			customRegisterer.waitFor();

			CompletionWaiter failedWaiter = machine.getTransitionedToWaiter(AfterRegistrationSyncFailed.class);

			Intent intent = new Intent();
			IntentUtils.addErrorInfo(intent, new ErrorInfo("intentional", 123));
			sendBroadcast("PUSH_DEVICE_REGISTERED", intent);

			failedWaiter.waitFor();
		}
	}

	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.addTest(new TestSetup(new TestSuite(AndroidPushTest.class)) {
			protected void setUp() throws Exception {
				setUpBeforeClass();
			}
			protected void tearDown() throws Exception {
				tearDownAfterClass();
			}
		});
		return suite;
	}

	// RSH2a
	public void test_push_activate() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation();
		BlockingQueue<Event> events = activation.machine.getEventReceiver(2); // CalledActivate + GotPushDeviceDetails
		assertInstanceOf(ActivationStateMachine.NotActivated.class, activation.machine.current);
		activation.rest.push.activate();
		Event event = events.take();
		assertInstanceOf(CalledActivate.class, event);
	}

	// RSH2b
	public void test_push_deactivate() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation();
		BlockingQueue<Event> events = activation.machine.getEventReceiver(1);
		assertInstanceOf(NotActivated.class, activation.machine.current);
		activation.rest.push.deactivate();
		Event event = events.take();
		assertInstanceOf(CalledDeactivate.class, event);
	}

	// RSH2c / RSH8g
	public void test_push_onNewRegistrationToken() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation();
		BlockingQueue<Event> events = activation.machine.getEventReceiver(1);
		final BlockingQueue<Callback<String>> tokenCallbacks = new ArrayBlockingQueue<>(1) ;

		activation.activationContext.onGetRegistrationToken = new Helpers.AblyFunction<Callback<String>, Void>() {
			@Override
			public Void apply(Callback<String> callback) throws AblyException {
				try {
					tokenCallbacks.put(callback);
				} catch (InterruptedException e) {
					throw AblyException.fromThrowable(e);
				}
				return null;
			}
		};

		activation.rest.push.activate(true); // This registers the listener for registration tokens.
		assertInstanceOf(CalledActivate.class, events.take());

		Callback<String> tokenCallback = tokenCallbacks.take();

		tokenCallback.onSuccess("foo");
		assertInstanceOf(GotPushDeviceDetails.class, events.take());

		tokenCallback.onSuccess("bar");
		assertInstanceOf(GotPushDeviceDetails.class, events.take());
	}

	// RSH2d / RSH8h
	public void test_push_onNewRegistrationTokenFailed() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation();
		BlockingQueue<Event> events = activation.machine.getEventReceiver(1);
		final BlockingQueue<Callback<String>> tokenCallbacks = new ArrayBlockingQueue<>(1) ;

		activation.activationContext.onGetRegistrationToken = new Helpers.AblyFunction<Callback<String>, Void>() {
			@Override
			public Void apply(Callback<String> callback) throws AblyException {
				try {
					tokenCallbacks.put(callback);
				} catch (InterruptedException e) {
					throw AblyException.fromThrowable(e);
				}
				return null;
			}
		};

		activation.rest.push.activate(true); // This registers the listener for registration tokens.
		assertInstanceOf(CalledActivate.class, events.take());

		Callback<String> tokenCallback = tokenCallbacks.take();

		tokenCallback.onError(new ErrorInfo("foo", 123, 123));
		Event event = events.take();
		assertInstanceOf(ActivationStateMachine.GettingPushDeviceDetailsFailed.class, event);
		assertEquals(123,((ActivationStateMachine.GettingPushDeviceDetailsFailed) event).reason.code);
	}

	// RSH8a, RSH8c
	public void test_push_device_persistence() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation(new Helpers.AblyFunction<ClientOptions, Void>() {
			@Override
			public Void apply(ClientOptions options) throws AblyException {
				options.clientId = "testClient";
				return null;
			}
		});

		// Fake-register the device.
		AsyncWaiter<Intent> customRegisterer = broadcastWaiter("PUSH_REGISTER_DEVICE");
		AsyncWaiter<Intent> activated = broadcastWaiter("PUSH_ACTIVATE");
		activation.rest.push.activate(true);

		LocalDevice device = activation.rest.device();
		assertEquals("testClient", device.clientId);
		assertNotNull(device.id);
		assertNotNull(device.deviceSecret);

		customRegisterer.waitFor();
		Intent intent = new Intent();
		intent.putExtra("deviceIdentityToken", "fakeToken");
		sendBroadcast("PUSH_DEVICE_REGISTERED", intent);
		activated.waitFor();

		assertEquals("fakeToken", activation.rest.device().deviceIdentityToken);

		// Load from persisted state.
		activation = new TestActivation(false);
		LocalDevice newDevice = activation.rest.device();
		assertEquals("fakeToken", newDevice.deviceIdentityToken);
		assertEquals(device.id, newDevice.id);
		assertEquals(device.deviceSecret, newDevice.deviceSecret);
		assertEquals(device.clientId, newDevice.clientId);
	}

	// RSH8d
	public void test_push_late_clientId_persisted() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation();

		assertNull(activation.rest.auth.clientId);
		assertNull(activation.rest.device().clientId);

		Auth.TokenParams params = new Auth.TokenParams();
		params.clientId = "testClient";
		activation.rest.auth.authorize(params, null);

		assertEquals("testClient", activation.rest.auth.clientId);
		assertEquals("testClient", activation.rest.device().clientId);

		activation = new TestActivation(false);
		assertEquals("testClient", activation.rest.device().clientId);
	}

	// RSH3a1
	public void test_NotActivated_on_CalledDeactivate() {
		TestActivation activation = new TestActivation();

		ActivationStateMachine.State state = new NotActivated(activation.machine);

		final Helpers.AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_DEACTIVATE");

		State to = state.transition(new CalledDeactivate());

		// RSH3a1a
		waiter.waitFor();
		assertNull(waiter.error);

		// RSH3a1b
		assertInstanceOf(NotActivated.class, to);
	}

	// RSH3a2a
	public void test_NotActivated_on_CalledActivate_with_DeviceToken() throws Exception {
		class TestCase extends TestCases.Base {
			private final String persistedClientId;
			private final String instanceClientId;
			private final ErrorInfo syncError;
			private final boolean useCustomRegistrar;
			private final Class<? extends Event> expectedEvent;
			private final Class<? extends State> expectedState;
			private final Integer expectedErrorCode;

			public TestCase(
					String name,
					String persistedClientId,
					String instanceClientId,
					boolean useCustomRegistrar,
					ErrorInfo syncError,
					Class<? extends Event> expectedEvent,
					Class<? extends State> expectedState,
					Integer expectedErrorCode
			) {
				super(name, null);
				this.persistedClientId = persistedClientId;
				this.instanceClientId = instanceClientId;
				this.useCustomRegistrar = useCustomRegistrar;
				this.syncError = syncError;
				this.expectedEvent = expectedEvent;
				this.expectedState = expectedState;
				this.expectedErrorCode = expectedErrorCode;
			}

			@Override
			public void run() throws Exception {
				// Register local device before doing anything, in order to trigger RSH3a2a.
				TestActivation activation = new TestActivation(new Helpers.AblyFunction<ClientOptions, Void>() {
					@Override
					public Void apply(ClientOptions options) throws AblyException {
						options.clientId = persistedClientId;
						return null;
					}
				});

				try {
					Helpers.AsyncWaiter<Intent> activateCallback = broadcastWaiter("PUSH_ACTIVATE");
					activation.rest.push.activate(false);
					activateCallback.waitFor();

					LocalDevice device = activation.rest.push.getLocalDevice();
					assertNotNull(device.id);
					assertNotNull(device.deviceIdentityToken);
					assertEquals(persistedClientId, device.clientId);


					// Now use a new instance, to force persistence consistency checking.
					activation = new TestActivation(new Helpers.AblyFunction<ClientOptions, Void>() {
						@Override
						public Void apply(ClientOptions options) throws AblyException {
							options.clientId = instanceClientId;
							return null;
						}
					}, false);

					Helpers.AsyncWaiter<Intent> registerCallback = useCustomRegistrar ? broadcastWaiter("PUSH_REGISTER_DEVICE") : null;
					activateCallback = broadcastWaiter("PUSH_ACTIVATE");

					CompletionWaiter calledActivateHandled = activation.machine.getEventHandledWaiter(CalledActivate.class);
					Helpers.AsyncWaiter<Helpers.RawHttpRequest> requestWaiter = null;

					if (!useCustomRegistrar) {
						if (syncError != null) {
							activation.httpTracker.mockResponse = Helpers.httpResponseFromErrorInfo(syncError);
						}

						requestWaiter = activation.httpTracker.getRequestWaiter();
						// Block until we've checked the intermediate WaitingForRegistrationSync state,
						// before the request's response causes another state transition.
						// Otherwise, our test would be racing against the request.
						activation.httpTracker.lockRequests();
					}

					activation.rest.push.activate(useCustomRegistrar);
					calledActivateHandled.waitFor();

					// RSH3a2a1: SyncRegistrationFailed may be enqueued (synchronously). In that
					// case, register callback or PUT request won't be invoked, and we'll go
					// synchronously to AfterRegistrationSyncFailed.
					if (activation.machine.current instanceof WaitingForRegistrationSync) {
						if (useCustomRegistrar) {
							// RSH3a2a2
							registerCallback.waitFor();
							assertNull(registerCallback.error);
						} else {
							// RSH3a2a3
							requestWaiter.waitFor();
							Helpers.RawHttpRequest request = requestWaiter.result;
							assertEquals("PUT", request.method);
							assertEquals("/push/deviceRegistrations/" + device.id, request.url.getPath());
						}

						// RSH3a2a4
						assertSize(0, activation.machine.pendingEvents);
						assertInstanceOf(WaitingForRegistrationSync.class, activation.machine.current);

						// Now wait for next event, when we may have an error.

						CompletionWaiter handled = activation.machine.getEventHandledWaiter();
						BlockingQueue<Event> events = activation.machine.getEventReceiver(1);

						if (useCustomRegistrar) {
							Intent intent = new Intent();
							if (syncError != null) {
								IntentUtils.addErrorInfo(intent, syncError);
							}
							sendBroadcast("PUSH_DEVICE_REGISTERED", intent);
						} else {
							activation.httpTracker.unlockRequests();
						}

						assertTrue(expectedEvent.isInstance(events.take()));
						assertNull(handled.waitFor());
					} // else: RSH3a2a1 validation failed

					// RSH3e2 or RSH3e3
					activateCallback.waitFor();
					if (expectedErrorCode != null) {
						assertNotNull(activateCallback.error);
						assertEquals(expectedErrorCode.intValue(), activateCallback.error.code);
					} else {
						assertNull(activateCallback.error);
					}
					assertTrue(expectedState.isInstance(activation.machine.current));
				} finally {
					activation.httpTracker.unlockRequests();
					/* delete the registration without sending (invalid) local device credentials */
					LocalDevice localDevice = activation.rest.push.getLocalDevice();
					String deviceId = localDevice.id;
					localDevice.reset();
					activation.rest.push.admin.deviceRegistrations.remove(deviceId);
				}
			}
		}

		TestCases testCases = new TestCases();

		// RSH3a2a1, RSH3a2a4, RSH3e3
		testCases.add(new TestCase(
				"clientId mismatch",
				"testClientId",
				"otherClientId",
				false,
				null,
				SyncRegistrationFailed.class,
				AfterRegistrationSyncFailed.class,
				61002
		));

		// RSH3a2a1, RSH3a2a2, RSH3a2a4, RSH3e2
		testCases.add(new TestCase(
				"ok with custom registerer",
				"testClientId",
				"testClientId",
				true,
				null,
				RegistrationSynced.class,
				WaitingForNewPushDeviceDetails.class,
				null
		));

		// RSH3a2a1, RSH3a2a2, RSH3a2a4, RSH3e3
		testCases.add(new TestCase(
				"failing with custom registerer",
				"testClientId",
				"testClientId",
				true,
				new ErrorInfo("test error", 123, 123),
				SyncRegistrationFailed.class,
				AfterRegistrationSyncFailed.class,
				123
		));

		// RSH3a2a1, RSH3a2a3, RSH3a2a4, RSH3e2
		testCases.add(new TestCase(
				"ok without custom registerer",
				"testClientId",
				"testClientId",
				false,
				null,
				RegistrationSynced.class,
				WaitingForNewPushDeviceDetails.class,
				null
		));

		// RSH3a2a1, RSH3a2a3, RSH3a2a4, RSH3e3
		testCases.add(new TestCase(
				"failing without custom registerer",
				"testClientId",
				"testClientId",
				false,
				new ErrorInfo("test error", 123, 123),
				SyncRegistrationFailed.class,
				AfterRegistrationSyncFailed.class,
				123
		));

		testCases.run();
	}

	// RSH3a3a
	public void test_NotActivated_on_GotPushDeviceDetails() throws InterruptedException {
		TestActivation activation = new TestActivation();
		State state = new NotActivated(activation.machine);

		State to = state.transition(new GotPushDeviceDetails());

		assertSize(0, activation.machine.pendingEvents);
		assertInstanceOf(NotActivated.class, to);
	}

	// RSH3a2b
	public void test_NotActivated_on_CalledActivate_with_registrationToken() throws InterruptedException, AblyException {
		TestActivation activation = new TestActivation();
		activation.rest.push.getActivationContext().onNewRegistrationToken(RegistrationToken.Type.FCM, "testToken");

		State state = new NotActivated(activation.machine);
		State to = state.transition(new CalledActivate());

		assertSize(1, activation.machine.pendingEvents);
		assertInstanceOf(GotPushDeviceDetails.class, activation.machine.pendingEvents.getLast());

		assertInstanceOf(WaitingForPushDeviceDetails.class, to);

		// RSH8b
		LocalDevice device = activation.rest.device();
		assertNotNull(device.id);
		assertNotNull(device.deviceSecret);
	}

	// RSH3a2c
	public void test_NotActivated_on_CalledActivate_without_registrationToken() throws InterruptedException {
		TestActivation activation = new TestActivation();
		State state = new NotActivated(activation.machine);
		State to = state.transition(new CalledActivate());

		assertSize(0, activation.machine.pendingEvents);

		assertInstanceOf(WaitingForPushDeviceDetails.class, to);
	}

	// RSH3b1
	public void test_WaitingForPushDeviceDetails_on_CalledActivate() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForPushDeviceDetails(activation.machine);
		State to = state.transition(new CalledActivate());

		assertSize(0, activation.machine.pendingEvents);

		// RSH3b1a
		assertInstanceOf(WaitingForPushDeviceDetails.class, to);
	}

	// RSH3b2
	public void test_WaitingForPushDeviceDetails_on_CalledDeactivate() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForPushDeviceDetails(activation.machine);

		final Helpers.AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_DEACTIVATE");

		State to = state.transition(new CalledDeactivate());

		// RSH3b2a
		waiter.waitFor();
		assertNull(waiter.error);

		assertSize(0, activation.machine.pendingEvents);

		// RSH3b2b
		assertInstanceOf(NotActivated.class, to);
	}

	// RSH3b3
	public void test_WaitingForPushDeviceDetails_on_GotPushDeviceDetails() throws Exception {
		class TestCase extends TestCases.Base {
			private final ErrorInfo registerError;
			private final boolean useCustomRegistrar;
			private final String deviceIdentityToken;
			private final Class<? extends Event> expectedEvent;
			private final Class<? extends State> expectedState;
			protected TestActivation activation;

			public TestCase(String name, boolean useCustomRegistrar, ErrorInfo error, String deviceIdentityToken, Class<? extends Event> expectedEvent, Class<? extends State> expectedState) {
				super(name, null);
				this.useCustomRegistrar = useCustomRegistrar;
				this.registerError = error;
				this.deviceIdentityToken = deviceIdentityToken;
				this.expectedEvent = expectedEvent;
				this.expectedState = expectedState;
			}

			@Override
			public void run() throws Exception {
				try {

					activation = new TestActivation();
					activation.activationContext.onGetRegistrationToken = new Helpers.AblyFunction<Callback<String>, Void>() {
						@Override
						public Void apply(Callback<String> callback) throws AblyException {
							// Ignore request; will send event manually below.
							return null;
						}
					};
					final Helpers.AsyncWaiter<Intent> registerCallback = useCustomRegistrar ? broadcastWaiter("PUSH_REGISTER_DEVICE") : null;
					final Helpers.AsyncWaiter<Intent> activateCallback = broadcastWaiter("PUSH_ACTIVATE");

					// Will move to WaitingForPushDeviceDetails.
					activation.rest.push.activate(useCustomRegistrar);

					CompletionWaiter handled = activation.machine.getEventHandledWaiter(GotPushDeviceDetails.class);
					Helpers.AsyncWaiter<Helpers.RawHttpRequest> requestWaiter = null;

					if (!useCustomRegistrar) {
						if (registerError != null) {
							activation.httpTracker.mockResponse = Helpers.httpResponseFromErrorInfo(registerError);
						}

						requestWaiter = activation.httpTracker.getRequestWaiter();
						// Block until we've checked the intermediate WaitingForDeviceRegistration state,
						// before the request's response causes another state transition.
						// Otherwise, our test would be racing against the request.
						activation.httpTracker.lockRequests();
					}

					// Will send GotPushDeviceDetails event.
					activation.rest.push.getActivationContext().onNewRegistrationToken(RegistrationToken.Type.FCM, "testToken");

					handled.waitFor();

					if (useCustomRegistrar) {
						// RSH3b3a
						registerCallback.waitFor();
						assertNull(registerCallback.error);
					} else {
						// RSH3b3b
						requestWaiter.waitFor();
						Helpers.RawHttpRequest request = requestWaiter.result;
						assertEquals("POST", request.method);
						assertEquals("/push/deviceRegistrations", request.url.getPath());
					}

					// RSH3b3d
					assertSize(0, activation.machine.pendingEvents);
					assertInstanceOf(WaitingForDeviceRegistration.class, activation.machine.current);

					// Now wait for next event, when we've got an deviceIdentityToken or an error.
					handled = activation.machine.getEventHandledWaiter();
					BlockingQueue<Event> events = activation.machine.getEventReceiver(1);

					if (useCustomRegistrar) {
						Intent intent = new Intent();
						if (registerError != null) {
							IntentUtils.addErrorInfo(intent, registerError);
						} else {
							intent.putExtra("deviceIdentityToken", deviceIdentityToken);
						}
						sendBroadcast("PUSH_DEVICE_REGISTERED", intent);
					} else {
						activation.httpTracker.unlockRequests();
					}

					assertTrue(expectedEvent.isInstance(events.take()));
					assertNull(handled.waitFor());

					// RSH3c2a
					if (useCustomRegistrar) {
						assertEquals(deviceIdentityToken, activation.rest.push.getLocalDevice().deviceIdentityToken);
					} else if (registerError == null) {
						// No error expected, so deviceIdentityToken should've been set by the server.
						assertNotNull(activation.rest.push.getLocalDevice().deviceIdentityToken);

					}

					// RSH3c2b, RSH3c3a
					activateCallback.waitFor();
					assertEquals(registerError, activateCallback.error);
					assertTrue(expectedState.isInstance(activation.machine.current));
				} finally {
					activation.httpTracker.unlockRequests();
					/* delete the registration without sending (invalid) local device credentials */
					LocalDevice localDevice = activation.rest.push.getLocalDevice();
					String deviceId = localDevice.id;
					localDevice.reset();
					activation.rest.push.admin.deviceRegistrations.remove(deviceId);
				}
			}
		}

		TestCases testCases = new TestCases();

		// RSH3c2
		testCases.add(new TestCase(
				"ok with custom registerer",
				true,
				null, "testDeviceToken",
				GotDeviceRegistration.class, // RSH3b3c
				WaitingForNewPushDeviceDetails.class /* RSH3c2c */));

		testCases.add(new TestCase(
				"ok with default registerer",
				false,
				null, "testDeviceToken",
				ActivationStateMachine.GotDeviceRegistration.class, // RSH3b3c
				WaitingForNewPushDeviceDetails.class /* RSH3c2c */));

		// RSH3c3
		testCases.add(new TestCase(
				"failing with custom registerer",
				true,
				new ErrorInfo("testError", 123), null,
				GettingDeviceRegistrationFailed.class, // RSH3b3c
				NotActivated.class /* RSH3c3b */));

		testCases.add(new TestCase(
				"failing with default registerer",
				false,
				new ErrorInfo("testError", 123), null,
				GettingDeviceRegistrationFailed.class, // RSH3b3c
				NotActivated.class /* RSH3c3b */));

		testCases.run();
	}

	// RSH3c1
	public void test_WaitingForDeviceRegistration_on_CalledActivate() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForDeviceRegistration(activation.machine);
		State to = state.transition(new CalledActivate());

		assertSize(0, activation.machine.pendingEvents);

		// RSH3c1a
		assertInstanceOf(WaitingForDeviceRegistration.class, to);
	}

	// RSH3d1
	public void test_WaitingForNewPushDeviceDetails_on_CalledActivate() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForNewPushDeviceDetails(activation.machine);

		final Helpers.AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_ACTIVATE");

		State to = state.transition(new CalledActivate());

		// RSH3d1a
		waiter.waitFor();
		assertNull(waiter.error);

		assertSize(0, activation.machine.pendingEvents);

		// RSH3d1b
		assertInstanceOf(WaitingForNewPushDeviceDetails.class, to);
	}

	// RSH3d2
	public void test_WaitingForNewPushDeviceDetails_on_CalledDeactivate() throws Exception {
		new DeactivateTest(WaitingForNewPushDeviceDetails.class) {
			@Override
			protected void setUpMachineState(TestCase testCase) throws AblyException {
				testCase.testActivation.registerAndWait();
			}
		}.run();
	}

	// RSH3d3
	public void test_WaitingForNewPushDeviceDetails_on_GotPushDeviceDetails() throws Exception {
		new UpdateRegistrationTest() {
			@Override
			protected void setUpMachineState(TestCase testCase) throws AblyException {
				testCase.testActivation.registerAndWait();
				testCase.testActivation.rest.push.activate(testCase.useCustomRegistrar);
			}
		}.run();
	}

	// RSH3e1
	public void test_WaitingForRegistrationUpdate_on_CalledActivate() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForRegistrationSync(activation.machine, null);

		final AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_ACTIVATE");

		State to = state.transition(new CalledActivate());

		// RSH3e1a
		waiter.waitFor();
		assertNull(waiter.error);

		assertSize(0, activation.machine.pendingEvents);

		// RSH3e1b
		assertInstanceOf(WaitingForRegistrationSync.class, to);
	}

	// RSH3e2
	public void test_WaitingForRegistrationUpdate_on_RegistrationUpdated() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForRegistrationSync(activation.machine, null);

		State to = state.transition(new RegistrationSynced());

		// RSH3e2a
		assertSize(0, activation.machine.pendingEvents);
		assertInstanceOf(WaitingForNewPushDeviceDetails.class, to);
	}

	// RSH3e3
	public void test_WaitingForRegistrationUpdate_on_UpdatingRegistrationFailed() {
		TestActivation activation = new TestActivation();
		State state = new WaitingForRegistrationSync(activation.machine, null);
		ErrorInfo reason = new ErrorInfo("test", 123);

		final AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_UPDATE_FAILED");

		State to = state.transition(new SyncRegistrationFailed(reason));

		// RSH3e3a
		waiter.waitFor();
		assertNull(waiter.result);
		assertEquals(reason, waiter.error);

		assertSize(0, activation.machine.pendingEvents);

		// RSH3e3b
		assertInstanceOf(AfterRegistrationSyncFailed.class, to);
	}

	// RSH3f1
	public void test_AfterRegistrationUpdateFailed_on_GotPushDeviceDetails() throws Exception {
		new UpdateRegistrationTest() {
			@Override
			protected void setUpMachineState(TestCase testCase) throws AblyException {
				testCase.testActivation.registerAndWait();
				testCase.testActivation.rest.push.activate(testCase.useCustomRegistrar);
				testCase.testActivation.moveToAfterRegistrationUpdateFailed();
			}
		}.run();
	}

	// RSH3f1
	public void test_AfterRegistrationUpdateFailed_on_CalledActivate() throws Exception {
		new UpdateRegistrationTest("PUSH_ACTIVATE") {
			@Override
			protected void setUpMachineState(TestCase testCase) throws AblyException {
				testCase.testActivation.registerAndWait();
				testCase.testActivation.moveToAfterRegistrationUpdateFailed();
			}

			@Override
			protected String sendInitialEvent(UpdateRegistrationTest.TestCase testCase) throws AblyException {
				testCase.testActivation.rest.push.activate(testCase.useCustomRegistrar);
				return "testTokenFailed";
			}
		}.run();
	}

	// RSH3f1
	public void test_AfterRegistrationUpdateFailed_on_CalledDeactivate() throws Exception {
		new DeactivateTest(AfterRegistrationSyncFailed.class) {
			@Override
			protected void setUpMachineState(TestCase testCase) throws AblyException {
				testCase.testActivation.registerAndWait();
				testCase.testActivation.moveToAfterRegistrationUpdateFailed();
			}
		}.run();
	}

	// RSH3g1
	public void test_WaitingForDeregistration_on_CalledDeactivate() throws Exception {
		TestActivation activation = new TestActivation();
		State state = new WaitingForDeregistration(activation.machine, null);

		State to = state.transition(new CalledDeactivate());

		assertSize(0, activation.machine.pendingEvents);
		assertInstanceOf(WaitingForDeregistration.class, to);
	}

	// RSH3g2
	public void test_WaitingForDeregistration_on_Deregistered() throws Exception {
		TestActivation activation = new TestActivation();
		State state = new WaitingForDeregistration(activation.machine, null);

		activation.rest.push.getLocalDevice().setDeviceIdentityToken("test");
		final Helpers.AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_DEACTIVATE");

		State to = state.transition(new Deregistered());

		// RSH3g2b
		waiter.waitFor();
		assertNull(waiter.error);

		// RSH3g2a
		assertNull(activation.rest.push.getLocalDevice().deviceIdentityToken);

		// RSH3g2c
		assertSize(0, activation.machine.pendingEvents);
		assertInstanceOf(NotActivated.class, to);
	}

	// RSH3g3
	public void test_WaitingForDeregistration_on_DeregistrationFailed() throws Exception {
		class TestCase extends TestCases.Base {
			private TestActivation testActivation;
			private State previousState;

			public TestCase(String name, TestActivation testActivation, State previousState) {
				super(name, null);
				this.testActivation = testActivation;
				this.previousState = previousState;
			}

			@Override
			public void run() throws Exception {
				State state = new WaitingForDeregistration(testActivation.machine, previousState);

				Helpers.AsyncWaiter<Intent> waiter = broadcastWaiter("PUSH_DEACTIVATE");
				ErrorInfo reason = new ErrorInfo("test", 123);

				State to = state.transition(new DeregistrationFailed(reason));

				// RSH3g3a
				waiter.waitFor();
				assertEquals(reason, waiter.error);

				// RSH3g3b
				assertSize(0, testActivation.machine.pendingEvents);
				assertInstanceOf(previousState.getClass(), to);
			}
		}

		TestCases testCases = new TestCases();

		TestActivation activation0 = new TestActivation();
		testCases.add(new TestCase(
				"from WaitingForNewPushDeviceDetails",
				activation0,
				new WaitingForNewPushDeviceDetails(activation0.machine)));

		TestActivation activation1 = new TestActivation();
		testCases.add(new TestCase(
				"from AfterRegistrationSyncFailed",
				activation1,
				new AfterRegistrationSyncFailed(activation1.machine)));

		testCases.run();
	}

	// RSH4a1
	public void test_PushChannel_subscribeDevice_not_registered() throws AblyException {
		TestActivation activation = new TestActivation();
		Channel channel = activation.rest.channels.get("pushenabled:foo");

		try {
			channel.push.subscribeDevice();
			fail("expected failure due to device not being registered");
		} catch (AblyException e) {
		} finally {
			String deviceId = activation.rest.push.getLocalDevice().id;
			if(deviceId != null) {
				PushBase.ChannelSubscription sub = PushBase.ChannelSubscription.forDevice(channel.name, deviceId);
				activation.rest.push.admin.channelSubscriptions.remove(sub);
			}
		}
	}

	// RSH4a2
	public void test_PushChannel_subscribeDevice_ok() throws AblyException {
		TestActivation activation = new TestActivation();
		Channel channel = activation.rest.channels.get("pushenabled:foo");
		PushBase.ChannelSubscription sub = null;

		try {
			activation.registerAndWait();
			sub = PushBase.ChannelSubscription.forDevice(channel.name, activation.rest.push.getLocalDevice().id);
			channel.push.subscribeDevice();

			PushBase.ChannelSubscription[] items = activation.rest.push.admin.channelSubscriptions.list(new Param[]{
					new Param("channel", channel.name),
					new Param("deviceId", sub.deviceId),
					new Param("fullWait", "true"),
			}).items();
			assertSize(1, items);
			assertEquals(items[0], sub);
		} finally {
			if(sub != null) {
				activation.rest.push.admin.channelSubscriptions.remove(sub);
			}
		}
	}

	// RSH4b1
	public void test_PushChannel_subscribeClient_not_registered() throws AblyException {
		TestActivation activation = new TestActivation();
		Channel channel = activation.rest.channels.get("pushenabled:foo");

		try {
			channel.push.subscribeClient();
			fail("expected failure due to device not having a client ID");
		} catch (AblyException e) {
		}
	}

	// RSH4b2
	public void test_PushChannel_subscribeClient_ok() throws AblyException {
		TestActivation activation = new TestActivation();
		final String testClientId = "testClient";
		activation.rest.auth.setClientId(testClientId);
		activation.rest.auth.authorize(new Auth.TokenParams() {{ clientId = testClientId; }}, null);

		PushBase.ChannelSubscription sub = null;
		try {
			activation.registerAndWait();

			Channel channel = activation.rest.channels.get("pushenabled:foo");
			sub = PushBase.ChannelSubscription.forClientId(channel.name, activation.rest.push.getLocalDevice().clientId);
			channel.push.subscribeClient();

			PushBase.ChannelSubscription[] items = activation.rest.push.admin.channelSubscriptions.list(new Param[]{
					new Param("channel", channel.name),
					new Param("clientId", sub.clientId),
					new Param("fullWait", "true"),
			}).items();
			assertSize(1, items);
			assertEquals(items[0], sub);
		} finally {
			if(sub != null) {
				activation.rest.push.admin.channelSubscriptions.remove(sub);
			}
		}
	}

	// RSH4c1
	public void test_PushChannel_unsubscribeDevice_not_registered() throws AblyException {
		TestActivation activation = new TestActivation();
		Channel channel = activation.rest.channels.get("pushenabled:foo");
		PushBase.ChannelSubscription sub = PushBase.ChannelSubscription.forDevice(channel.name, activation.rest.push.getLocalDevice().id);

		try {
			channel.push.unsubscribeDevice();
			fail("expected failure due to device not being registered");
		} catch (AblyException e) {
		}
	}

	// RSH4c2
	public void test_PushChannel_unsubscribeDevice_ok() throws AblyException {
		TestActivation activation = new TestActivation();
		Channel channel = activation.rest.channels.get("pushenabled:foo");
		PushBase.ChannelSubscription sub = null;

		try {
			activation.registerAndWait();
			sub = PushBase.ChannelSubscription.forDevice(channel.name, activation.rest.push.getLocalDevice().id);

			activation.rest.push.admin.channelSubscriptions.save(sub);

			channel.push.unsubscribeDevice();

			PushBase.ChannelSubscription[] items = activation.rest.push.admin.channelSubscriptions.list(new Param[]{
					new Param("channel", channel.name),
					new Param("deviceId", sub.deviceId),
					new Param("fullWait", "true"),
			}).items();
			assertSize(0, items);
		} finally {
			if(sub != null) {
				activation.rest.push.admin.channelSubscriptions.remove(sub);
			}
		}
	}

	// RSH4d1
	public void test_PushChannel_unsubscribeClient_not_registered() throws AblyException {
		TestActivation activation = new TestActivation();
		Channel channel = activation.rest.channels.get("pushenabled:foo");
		PushBase.ChannelSubscription sub = PushBase.ChannelSubscription.forClientId(channel.name, activation.rest.push.getLocalDevice().clientId);

		try {
			channel.push.unsubscribeClient();
			fail("expected failure due to device not having a client ID");
		} catch (AblyException e) {
		}
	}

	// RSH4d2
	public void test_PushChannel_unsubscribeClient_ok() throws AblyException {
		TestActivation activation = new TestActivation();
		final String testClientId = "testClient";
		activation.rest.auth.setClientId(testClientId);
		activation.rest.auth.authorize(new Auth.TokenParams() {{ clientId = testClientId; }}, null);

		Channel channel = activation.rest.channels.get("pushenabled:foo");
		PushBase.ChannelSubscription sub = null;

		try {
			activation.registerAndWait();
			sub = PushBase.ChannelSubscription.forClientId(channel.name, activation.rest.push.getLocalDevice().clientId);

			activation.rest.push.admin.channelSubscriptions.save(sub);

			channel.push.unsubscribeClient();

			PushBase.ChannelSubscription[] items = activation.rest.push.admin.channelSubscriptions.list(new Param[]{
					new Param("channel", channel.name),
					new Param("clientId", sub.clientId),
					new Param("fullWait", "true"),
			}).items();
			assertSize(0, items);
		} finally {
			if(sub != null) {
				activation.rest.push.admin.channelSubscriptions.remove(sub);
			}
		}
	}

	// RSH4e
	public void test_PushChannel_listSubscriptions() throws Exception {
		class TestCase extends TestCases.Base {
			private boolean useClientId;
			private TestActivation testActivation;

			public TestCase(String name, boolean useClientId) {
				super(name, null);
				this.useClientId = useClientId;
			}

			@Override
			public void run() throws Exception {
				testActivation = new TestActivation();
				if (useClientId) {
					final String testClientId = "testClient";
					testActivation.rest.auth.setClientId(testClientId);
					testActivation.rest.auth.authorize(new Auth.TokenParams() {{ clientId = testClientId; }}, null);
				} else {
					testActivation.rest.auth.authorize(null, null);
				}

				testActivation.registerAndWait();
				DeviceDetails otherDevice = DeviceDetails.fromJsonObject(JsonUtils.object()
						.add("id", "other")
						.add("platform", "android")
						.add("formFactor", "tablet")
						.add("metadata", JsonUtils.object())
						.add("push", JsonUtils.object()
								.add("recipient", JsonUtils.object()
										.add("transportType", "fcm")
										.add("registrationToken", "qux")))
						.toJson());

				String deviceId = testActivation.rest.push.getLocalDevice().id;

				Push.ChannelSubscription[] fixtures = new Push.ChannelSubscription[] {
					PushBase.ChannelSubscription.forDevice("pushenabled:foo", deviceId),
					PushBase.ChannelSubscription.forDevice("pushenabled:foo", "other"),
					PushBase.ChannelSubscription.forDevice("pushenabled:bar", deviceId),
					PushBase.ChannelSubscription.forClientId("pushenabled:foo", "testClient"),
					PushBase.ChannelSubscription.forClientId("pushenabled:foo", "otherClient"),
					PushBase.ChannelSubscription.forClientId("pushenabled:bar", "testClient"),
				};

				try {
					testActivation.adminRest.push.admin.deviceRegistrations.save(otherDevice);

					for (PushBase.ChannelSubscription sub : fixtures) {
						testActivation.adminRest.push.admin.channelSubscriptions.save(sub);
					}

					Push.ChannelSubscription[] got = testActivation.rest.channels.get("pushenabled:foo").push.listSubscriptions().items();

					ArrayList<Push.ChannelSubscription> expected = new ArrayList<>(2);
					expected.add(PushBase.ChannelSubscription.forDevice("pushenabled:foo", deviceId));
					if (useClientId) {
						expected.add(PushBase.ChannelSubscription.forClientId("pushenabled:foo", "testClient"));
					}

					assertArrayUnorderedEquals(expected.toArray(), got);
				} finally {
					testActivation.adminRest.push.admin.deviceRegistrations.remove(otherDevice);
					for (PushBase.ChannelSubscription sub : fixtures) {
						testActivation.adminRest.push.admin.channelSubscriptions.remove(sub);
					}
				}
			}
		}

		TestCases testCases = new TestCases();

		testCases.add(new TestCase("without client ID", false));
		testCases.add(new TestCase("with client ID", true));

		testCases.run();
	}

	public void test_Realtime_push_interface() throws Exception {
		AblyRealtime realtime = new AblyRealtime(new ClientOptions() {{
			autoConnect = false;
			key = "madeup";
		}});
		realtime.setAndroidContext(getContext());
		assertInstanceOf(LocalDevice.class, realtime.push.getLocalDevice());
		assertInstanceOf(Push.class, realtime.push);
		assertInstanceOf(PushChannel.class, realtime.channels.get("test").push);
	}

	// This is all copied and pasted from ParameterizedTest, since I can't inherit from it.
	// I need to inherit from AndroidPushTest, and Java doesn't have multiple inheritance
	// or mixins or something like that.

	protected static Setup.TestVars testVars;

	public static void setUpBeforeClass() throws Exception {
		testVars = Setup.getTestVars();
	}

	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	private Setup.TestParameters testParams = Setup.TestParameters.getDefault();

	protected DebugOptions createOptions() throws AblyException {
		return testVars.createOptions(testParams);
	}

	protected DebugOptions createOptions(String key) throws AblyException {
		return testVars.createOptions(key, testParams);
	}

	protected void fillInOptions(ClientOptions opts) {
		testVars.fillInOptions(opts, testParams);
	}

	private class TestActivationContext extends ActivationContext {
		public Helpers.AblyFunction<Callback<String>, Void> onGetRegistrationToken;

		TestActivationContext(Context context) {
			super(context);
			this.onGetRegistrationToken = new Helpers.AblyFunction<Callback<String>, Void>() {
				@Override
				public Void apply(Callback<String> callback) throws AblyException {
					callback.onSuccess(ULID.random());
					return null;
				}
			};
		}

		@Override
		public synchronized ActivationStateMachine getActivationStateMachine() {
			if(activationStateMachine == null) {
				activationStateMachine = new TestActivationStateMachine(this);
			}
			return activationStateMachine;
		}

		@Override
		protected void getRegistrationToken(final Callback<String> callback) {
			try {
				this.onGetRegistrationToken.apply(callback);
			} catch (AblyException e) {
				callback.onError(ErrorInfo.fromThrowable(e));
			}
		}
	}

	private class TestActivationStateMachine extends ActivationStateMachine {
		class EventOrStateWaiter extends CompletionWaiter {
			Class<? extends Event> event;
			Class<? extends State> state;

			public boolean shouldFire(State state, Event event) {
				if (this.state != null) {
					if (this.state.isInstance(state)) {
						return true;
					}
				} else if (this.event != null) {
					if (this.event.isInstance(event)) {
						return true;
					}
				} else {
					return true;
				}
				return false;
			}
		}

		private BlockingQueue<Event> events = null;
		private EventOrStateWaiter waiter;
		private Class<? extends State> waitingForState;

		public TestActivationStateMachine(ActivationContext activationContext) {
			super(activationContext);
		}

		@Override
		public synchronized boolean handleEvent(Event event) {
			if (events != null) {
				try {
					events.put(event);
				} catch (InterruptedException e) {}
			}

			boolean ok = super.handleEvent(event);

			if (waiter != null && waiter.shouldFire(current, event)) {
				CompletionWaiter w = waiter;
				waiter = null;
				w.onSuccess();
			}
			return ok;
		}

		@Override
		public boolean reset() {
			waiter = null;
			events = null;
			return super.reset();
		}

		public BlockingQueue<Event> getEventReceiver(int capacity) {
			events = new ArrayBlockingQueue<Event>(capacity);
			return events;
		}

		public CompletionWaiter getEventHandledWaiter() {
			return getEventHandledWaiter(null);
		}

		public CompletionWaiter getEventHandledWaiter(final Class<? extends Event> e) {
			waiter = new EventOrStateWaiter() {{
				event = e;
			}};
			return waiter;
		}

		public CompletionWaiter getTransitionedToWaiter(final Class<? extends State> s) {
			waiter = new EventOrStateWaiter() {{
				state = s;
			}};
			return waiter;
		}
	}

	private AsyncWaiter<Intent> broadcastWaiter(String event) {
		final AsyncWaiter<Intent> waiter = new AsyncWaiter<Intent>();
		BroadcastReceiver onceReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(this);
				ErrorInfo error = IntentUtils.getErrorInfo(intent);
				if (error == null) {
					waiter.onSuccess(intent);
				} else {
					waiter.onError(error);
				}
			}
		};
		IntentFilter filter = new IntentFilter("io.ably.broadcast." + event);
		LocalBroadcastManager.getInstance(getContext().getApplicationContext()).registerReceiver(onceReceiver, filter);
		return waiter;
	}

	private void sendBroadcast(String name, Intent intent) {
		intent.setAction("io.ably.broadcast." + name);
		LocalBroadcastManager.getInstance(getContext().getApplicationContext()).sendBroadcast(intent);
	}

	private abstract class DeactivateTest {
		private Class<? extends State> previousState;

		DeactivateTest(Class<? extends State> previousState) {
			this.previousState = previousState;
		}

		protected abstract void setUpMachineState(TestCase testCase) throws AblyException;

		class TestCase extends TestCases.Base {
			private final ErrorInfo deregisterError;
			private final boolean useCustomDeregisterer;
			private final Class<? extends Event> expectedEvent;
			private final Class<? extends State> expectedState;
			protected TestActivation testActivation;

			public TestCase(String name, boolean useCustomDeregisterer, ErrorInfo error, Class<? extends Event> expectedEvent, Class<? extends State> expectedState) {
				super(name, null);
				this.useCustomDeregisterer = useCustomDeregisterer;
				this.deregisterError = error;
				this.expectedEvent = expectedEvent;
				this.expectedState = expectedState;
			}

			@Override
			public void run() throws Exception {
				try {
					testActivation = new TestActivation();

					setUpMachineState(this);

					final AsyncWaiter<Intent> deregisterCallback = useCustomDeregisterer ? broadcastWaiter("PUSH_DEREGISTER_DEVICE") : null;
					final AsyncWaiter<Intent> deactivateCallback = broadcastWaiter("PUSH_DEACTIVATE");
					AsyncWaiter<Helpers.RawHttpRequest> requestWaiter = null;

					if (!useCustomDeregisterer) {
						if (deregisterError != null) {
							testActivation.httpTracker.mockResponse = Helpers.httpResponseFromErrorInfo(deregisterError);
						}

						requestWaiter = testActivation.httpTracker.getRequestWaiter();
						// Block until we've checked the intermediate WaitingForDeregistration state,
						// before the request's response causes another state transition.
						// Otherwise, our test would be racing against the request.
						testActivation.httpTracker.lockRequests();
					}

					CompletionWaiter deactivatingWaiter = testActivation.machine.getTransitionedToWaiter(WaitingForDeregistration.class);
					// Will send a CalledDeactivate event.
					testActivation.rest.push.deactivate(useCustomDeregisterer);
					deactivatingWaiter.waitFor();

					if (useCustomDeregisterer) {
						// RSH3d2a
						deregisterCallback.waitFor();
						assertNull(deregisterCallback.error);
					} else {
						// RSH3d2b
						requestWaiter.waitFor();
						Helpers.RawHttpRequest request = requestWaiter.result;
						assertEquals("DELETE", request.method);
						assertEquals("/push/deviceRegistrations/" + testActivation.rest.push.getLocalDevice().id, request.url.getPath());
					}

					// RSH3d2d
					assertInstanceOf(WaitingForDeregistration.class, testActivation.machine.current);

					// Now wait for next event, after deregistration.
					CompletionWaiter handled = testActivation.machine.getEventHandledWaiter();
					BlockingQueue<Event> events = testActivation.machine.getEventReceiver(1);

					if (useCustomDeregisterer) {
						Intent intent = new Intent();
						if (deregisterError != null) {
							IntentUtils.addErrorInfo(intent, deregisterError);
						}
						sendBroadcast("PUSH_DEVICE_DEREGISTERED", intent);
					} else {
						testActivation.httpTracker.unlockRequests();
					}

					assertTrue(expectedEvent.isInstance(events.take()));
					assertNull(handled.waitFor());

					if (deregisterError == null) {
						// RSH3g2a
						assertNull(testActivation.rest.push.getLocalDevice().deviceIdentityToken);
					} else {
						// RSH3g3a
						assertNotNull(testActivation.rest.push.getLocalDevice().deviceIdentityToken);
					}

					// RSH3g2b, RSH3g3a
					deactivateCallback.waitFor();
					assertEquals(deregisterError, deactivateCallback.error);
					assertInstanceOf(expectedState, testActivation.machine.current);
				} finally {
					testActivation.httpTracker.unlockRequests();
					testActivation.rest.push.admin.deviceRegistrations.remove(testActivation.rest.push.getLocalDevice());
				}
			}
		}

		public void run() throws Exception {
			TestCases testCases = new TestCases();

			// RSH3g2
			testCases.add(new TestCase(
					"ok with custom deregisterer",
					true,
					null,
					Deregistered.class,
					NotActivated.class /* RSH3g2c */));

			testCases.add(new TestCase(
					"ok with default deregisterer",
					false,
					null,
					Deregistered.class,
					NotActivated.class /* RSH3g2c */));

			// RSH3g3
			testCases.add(new TestCase(
					"failing with custom deregisterer",
					true,
					new ErrorInfo("testError", 123),
					DeregistrationFailed.class,
					previousState /* RSH3g3b */));

			testCases.add(new TestCase(
					"failing with default deregisterer",
					false,
					new ErrorInfo("testError", 123),
					DeregistrationFailed.class,
					previousState /* RSH3g3b */));

			testCases.run();
		}
	}

	private abstract class UpdateRegistrationTest {
		private final String onFailedEvent;

		protected abstract void setUpMachineState(TestCase testCase) throws AblyException;

		UpdateRegistrationTest() {
			this("PUSH_UPDATE_FAILED");
		}

		UpdateRegistrationTest(String onFailedEvent) {
			this.onFailedEvent = onFailedEvent;
		}

		class TestCase extends TestCases.Base {
			private final ErrorInfo updateError;
			private final boolean useCustomRegistrar;
			private final Class<? extends Event> expectedEvent;
			private final Class<? extends State> expectedState;
			protected TestActivation testActivation;

			public TestCase(String name, boolean useCustomRegistrar, ErrorInfo error, Class<? extends Event> expectedEvent, Class<? extends State> expectedState) {
				super(name, null);
				this.useCustomRegistrar = useCustomRegistrar;
				this.updateError = error;
				this.expectedEvent = expectedEvent;
				this.expectedState = expectedState;
			}

			@Override
			public void run() throws Exception {
				try {
					testActivation = new TestActivation();

					setUpMachineState(this);
					final boolean isExpectingRegistrationValidation = testActivation.machine.current instanceof AfterRegistrationSyncFailed;

					final AsyncWaiter<Intent> registerCallback = useCustomRegistrar ? broadcastWaiter("PUSH_REGISTER_DEVICE") : null;
					final AsyncWaiter<Intent> updateFailedCallback = updateError != null ? broadcastWaiter(onFailedEvent) : null;
					AsyncWaiter<Helpers.RawHttpRequest> requestWaiter = null;

					if (!useCustomRegistrar) {
						if (updateError != null) {
							testActivation.httpTracker.mockResponse = Helpers.httpResponseFromErrorInfo(updateError);
						}

						requestWaiter = testActivation.httpTracker.getRequestWaiter();
						// Block until we've checked the intermediate WaitingForDeregistration state,
						// before the request's response causes another state transition.
						// Otherwise, our test would be racing against the request.
						testActivation.httpTracker.lockRequests();
					}

					CompletionWaiter updatingWaiter = testActivation.machine.getTransitionedToWaiter(WaitingForRegistrationSync.class);
					String updatedRegistrationToken = sendInitialEvent(this);
					updatingWaiter.waitFor();

					if (useCustomRegistrar) {
						// RSH3d3a
						registerCallback.waitFor();
						assertNull(registerCallback.error);
					} else {
						requestWaiter.waitFor();
						Helpers.RawHttpRequest request = requestWaiter.result;
						assertEquals("/push/deviceRegistrations/"+testActivation.rest.push.getLocalDevice().id, request.url.getPath());
						String authToken = Base64Coder.decodeString(request.requestHeaders.get("X-Ably-DeviceToken").get(0));
						assertEquals(testActivation.rest.push.getLocalDevice().deviceIdentityToken, authToken);

						JsonObject requestBody = (JsonObject)Serialisation.msgpackToGson(request.requestBody.getEncoded());
						JsonObject requestRecipient = requestBody.getAsJsonObject("push").getAsJsonObject("recipient");
						assertEquals("fcm", requestRecipient.getAsJsonPrimitive("transportType").getAsString());
						assertEquals(updatedRegistrationToken, requestRecipient.getAsJsonPrimitive("registrationToken").getAsString());

						if(isExpectingRegistrationValidation) {
							// RSH3f1a: PUT the entire DeviceDetails
							assertEquals("PUT", request.method);
							assertTrue(requestBody.has("deviceSecret"));
							assertTrue(requestBody.has("clientId"));
						} else {
							// RSH3d3b: PATCH the updated members
							assertEquals("PATCH", request.method);
							assertFalse(requestBody.has("deviceSecret"));
							assertFalse(requestBody.has("clientId"));
						}
					}

					// RSH3d3d
					assertInstanceOf(WaitingForRegistrationSync.class, testActivation.machine.current);

					// Now wait for next event, after updated.
					CompletionWaiter handled = testActivation.machine.getEventHandledWaiter();
					BlockingQueue<Event> events = testActivation.machine.getEventReceiver(1);

					if (useCustomRegistrar) {
						Intent intent = new Intent();
						if (updateError != null) {
							IntentUtils.addErrorInfo(intent, updateError);
						}
						sendBroadcast("PUSH_DEVICE_REGISTERED", intent);
					} else {
						testActivation.httpTracker.unlockRequests();
					}

					assertTrue(expectedEvent.isInstance(events.take()));
					assertNull(handled.waitFor());

					if (updateError != null) {
						// RSH3e3a
						updateFailedCallback.waitFor();
						assertEquals(updateError, updateFailedCallback.error);
					}
					// RSH3e2a, RSH3e3b
					assertTrue(expectedState.isInstance(testActivation.machine.current));
				} finally {
					testActivation.httpTracker.unlockRequests();
					testActivation.rest.push.admin.deviceRegistrations.remove(testActivation.rest.push.getLocalDevice());
				}
			}
		}

		public void run() throws Exception {
			TestCases testCases = new TestCases();

			// RSH3e2
			testCases.add(new TestCase(
					"ok with custom registerer",
					true,
					null,
					RegistrationSynced.class,
					WaitingForNewPushDeviceDetails.class));

			testCases.add(new TestCase(
					"ok with default registerer",
					false,
					null,
					RegistrationSynced.class,
					WaitingForNewPushDeviceDetails.class));

			// RSH3e3
			testCases.add(new TestCase(
					"failing with custom registerer",
					true,
					new ErrorInfo("testError", 123),
					SyncRegistrationFailed.class,
					AfterRegistrationSyncFailed.class));

			testCases.add(new TestCase(
					"failing with default registerer",
					false,
					new ErrorInfo("testError", 123),
					SyncRegistrationFailed.class,
					AfterRegistrationSyncFailed.class));

			testCases.run();
		}

		protected String sendInitialEvent(TestCase testCase) throws AblyException {
			// Will send GotPushDeviceDetails event.
			CalledActivate.useCustomRegistrar(testCase.useCustomRegistrar, PreferenceManager.getDefaultSharedPreferences(getContext()));
			testCase.testActivation.rest.push.getActivationContext().onNewRegistrationToken(RegistrationToken.Type.FCM, "testTokenUpdated");
			return "testTokenUpdated";
		}
	}
}
