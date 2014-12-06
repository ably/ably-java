package io.ably.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.ably.realtime.AblyRealtime;
import io.ably.realtime.Connection.ConnectionState;
import io.ably.test.realtime.Helpers.ConnectionWaiter;
import io.ably.test.realtime.RealtimeSetup.TestVars;
import io.ably.types.AblyException;
import io.ably.types.ErrorInfo;
import io.ably.types.Options;

import org.junit.Test;

public class RealtimeConnectFail {

	/**
	 * Verify that the connection enters the failed state, after attempting
	 * to connect with invalid app
	 */
	@Test
	public void connect_fail_notfound_error() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			Options opts = testVars.createOptions("not_an_app.invalid_key_id:invalid_key_value");
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
	 * to connect with invalid app
	 */
	@Test
	public void connect_fail_authorized_error() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			Options opts = testVars.createOptions(testVars.appId + ".invalid_key_id:invalid_key_value");
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
			TestVars testVars = RealtimeSetup.getTestVars();
			Options opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.wsHost = "non.existent.host";
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
	 * Verify that the connection enters the disconnected state, after attempting
	 * to connect to a non-existent ws host
	 */
	@Test
	public void connect_fail_suspended() {
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			Options opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.wsHost = "non.existent.host";
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

}
