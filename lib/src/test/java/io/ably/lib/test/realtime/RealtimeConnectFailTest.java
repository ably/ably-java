package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.rest.Auth;
import io.ably.lib.transport.Defaults;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth.TokenCallback;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RealtimeConnectFailTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Verify that the connection enters the failed state, after attempting
	 * to connect with invalid app
	 */
	@Test
	public void connect_fail_notfound_error() throws AblyException {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions("not_an_app.invalid_key_id:invalid_key_value");
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
			assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
			assertEquals("Verify correct error code is given", 404, fail.statusCode);
		} finally {
			ably.close();
		}
	}

	/**
	 * Verify that the connection enters the failed state, after attempting
	 * to connect with invalid key
	 */
	@Test
	public void connect_fail_authorized_error() throws AblyException {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.appId + ".invalid_key_id:invalid_key_value");
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
			assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
			assertEquals("Verify correct error code is given", 401, fail.statusCode);
		} finally {
			ably.close();
		}
	}

	/**
	 * Verify that the connection enters the disconnected state, after attempting
	 * to connect to a non-existent ws host
	 */
	@Test
	public void connect_fail_disconnected() throws AblyException {
		TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "non.existent.host";
		opts.environment = null;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

		connectionWaiter.waitFor(ConnectionState.disconnected);
		assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);
		ably.close();
		connectionWaiter.waitFor(ConnectionState.closed);
		assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
	}

	/**
	 * Verify that the connection enters the suspended state, after multiple attempts
	 * to connect to a non-existent ws host
	 */
	@Test
	public void connect_fail_suspended() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.realtimeHost = "non.existent.host";
			opts.environment = null;
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.suspended);
			/* Wait 1s to force bug where it changes to disconnected right after
			 * changing to suspended. Without this it fails only intermittently
			 * when that bug is present. */
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
			assertEquals("Verify suspended state is reached", ConnectionState.suspended, ably.connection.state);
			assertTrue("Verify multiple connect attempts", connectionWaiter.getCount(ConnectionState.connecting) > 1);
			assertTrue("Verify multiple connect attempts", connectionWaiter.getCount(ConnectionState.disconnected) > 1);
			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify that the connection in the disconnected state (after attempts to
	 * connect to a non-existent ws host) allows an immediate explicit connect
	 * attempt, instead of ignoring the explicit connect and waiting till the
	 * next scheduled retry.
	 */
	@Test
	public void connect_while_disconnected() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.realtimeHost = "non.existent.host";
			opts.environment = null;
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.disconnected);
			assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

			long before = System.currentTimeMillis();
			ably.connection.connect();
			connectionWaiter.waitFor(ConnectionState.connecting);
			assertTrue("Verify explicit connect is actioned immediately", System.currentTimeMillis() - before < 1000L);

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify that the connection enters the disconnected state, after a token
	 * used for successful connection expires
	 */
	@Test
	public void connect_token_expire_disconnected() {
		try {
			final Setup.TestVars optsTestVars = Setup.getTestVars();
			ClientOptions optsForToken = optsTestVars.createOptions(optsTestVars.keys[0].keyStr);
			optsForToken.logLevel = Log.VERBOSE;
			final AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 8000L; }}, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* implement callback, using Ably instance with key */
			final class TokenGenerator implements TokenCallback {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					++cbCount;
					return ablyForToken.auth.requestToken(params, null);
				}
				public int getCbCount() { return cbCount; }
				private int cbCount = 0;
			};

			TokenGenerator authCallback = new TokenGenerator();

			/* create Ably realtime instance without key */
			final TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions();
			opts.tokenDetails = tokenDetails;
			opts.authCallback = authCallback;
			opts.logLevel = Log.VERBOSE;
			AblyRealtime ably = new AblyRealtime(opts);

			/* wait for connected state */
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* wait for disconnected state (on token expiry), with timeout */
			connectionWaiter.waitFor(ConnectionState.disconnected, 1, 30000L);
			assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

			/* wait for connected state (on token renewal) */
			connectionWaiter.waitFor(ConnectionState.connected, 1, 30000L);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* verify that our token generator was called */
			assertEquals("Expected token generator to be called", 1, authCallback.getCbCount());

			/* end */
			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify that the connection fails when attempting to recover with a
	 * malformed connection id
	 */
	@Test
	public void connect_invalid_recover_fail() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.recover = "not_a_valid_connection_id:99";
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
			assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
			assertEquals("Verify correct error code is given", 80018, fail.code);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			ably.close();
		}
	}

	/**
	 * Verify that the connection creates a new connection but reports
	 * a recovery error, when attempting to recover with an unknown
	 * connection id
	 */
	@Test
	public void connect_unknown_recover_fail() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			String recoverConnectionId = "0123456789abcdef-99";
			opts.recover = recoverConnectionId + ":0";
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			ErrorInfo connectedError = connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			assertNotNull("Verify error is returned", connectedError);
			assertEquals("Verify correct error code is given", 80008, connectedError.code);
			assertFalse("Verify new connection id is assigned", recoverConnectionId.equals(ably.connection.key));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			ably.close();
		}
	}

	/**
	 * Test that connection manager correctly fails messages set stored in message queue
	 */

	@Test
	public void connect_test_queued_messages_on_failure() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			final int[] numberOfErrors = new int[]{0};

			// assume we are in connecting state now
			ably.connection.connectionManager.send(new ProtocolMessage(), true, new CompletionListener() {
				@Override
				public void onSuccess() {
					fail("Unexpected success sending message");
				}

				@Override
				public void onError(ErrorInfo reason) {
					numberOfErrors[0]++;
				}
			});

			// transition to suspended state failing messages
			ably.connection.connectionManager.requestState(ConnectionState.suspended);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
			// transition once more to ensure onError() won't be called twice
			ably.connection.connectionManager.requestState(ConnectionState.closed);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}

			// onError() should be called only once
			assertEquals("Verifying number of onError() calls", numberOfErrors[0], 1);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception");
		} finally {
			if (ably != null)
				ably.close();
		}
	}

	/**
	 * Allow token to expire and return already expired token after that. Test that the connection state
	 * is changed in the correct way and without duplicates:
	 *
	 * connecting -> connected -> disconnected -> connecting -> disconnected -> failed
	 */
	@Test
	public void connect_reauth_failure_state_flow_test() {
		AblyRealtime ablyRealtime = null;
		AblyRest ablyRest = null;
		try {
			/* To trigger the bug when connection is going to suspended if total connection time is more than
			 * TIMEOUT_SUSPEND we set TIMEOUT_SUSPEND to smaller value
			 */
			Defaults.TIMEOUT_SUSPEND = 5000;
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);

			ablyRest = new AblyRest(opts);
			final TokenDetails tokenDetails = ablyRest.auth.requestToken(new TokenParams() {{ ttl = 8000L; }}, null);
			assertNotNull("Expected token value", tokenDetails.token);

			final ArrayList<ConnectionState> stateHistory = new ArrayList<>();

			ClientOptions optsForRealtime = testVars.createOptions();
			optsForRealtime.authCallback = new TokenCallback() {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					// return already expired token
					return tokenDetails;
				}
			};
			optsForRealtime.tokenDetails = tokenDetails;
			ablyRealtime = new AblyRealtime(optsForRealtime);
			ablyRealtime.connection.on(new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					synchronized (stateHistory) {
						System.out.println(state.current);
						stateHistory.add(state.current);
					}
				}
			});

			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.failed);

			List<ConnectionState> correctHistory = Arrays.asList(
					ConnectionState.connecting,
					ConnectionState.connected,
					ConnectionState.disconnected,
					ConnectionState.connecting,
					ConnectionState.disconnected,
					ConnectionState.failed
			);

			System.out.println(correctHistory.toString());
			synchronized (stateHistory) {
				assertTrue("Verifying state change history", stateHistory.toString().equals(correctHistory.toString()));
			}

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			Defaults.TIMEOUT_SUSPEND = 120000;
			if (ablyRealtime != null)
				ablyRealtime.close();
		}
	}
}
