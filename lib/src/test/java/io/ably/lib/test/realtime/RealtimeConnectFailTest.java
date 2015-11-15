package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
	public void connect_fail_notfound_error() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions("not_an_app.invalid_key_id:invalid_key_value");
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
			assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
			assertEquals("Verify correct error code is given", 404, fail.statusCode);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			ably.close();
		}
	}

	/**
	 * Verify that the connection enters the failed state, after attempting
	 * to connect with invalid key
	 */
	@Test
	public void connect_fail_authorized_error() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.appId + ".invalid_key_id:invalid_key_value");
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
			assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
			assertEquals("Verify correct error code is given", 401, fail.statusCode);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			ably.close();
		}
	}

	/**
	 * Verify that the connection enters the disconnected state, after attempting
	 * to connect to a non-existent ws host
	 */
	@Test
	public void connect_fail_disconnected() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.realtimeHost = "non.existent.host";
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.disconnected);
			assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);
			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify that the connection enters the suspended state, after multiple attempts
	 * to connect to a non-existent ws host
	 */
	//@Test
	public void connect_fail_suspended() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.realtimeHost = "non.existent.host";
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.suspended);
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
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, new TokenParams() {{ ttl = 2000L; }});
			assertNotNull("Expected token value", tokenDetails.token);

			/* implement callback, using Ably instance with key */
			final class TokenGenerator implements TokenCallback {
				@Override
				public Object getTokenRequest(TokenParams params) throws AblyException {
					++cbCount;
					return ablyForToken.auth.requestToken(null, params);
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

			/* wait for disconnected state (on token expiry) */
			connectionWaiter.waitFor(ConnectionState.disconnected);
			assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

			/* wait for connected state (on token renewal) */
			connectionWaiter.waitFor(ConnectionState.connected);
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
}
