package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class RealtimeConnectTest extends ParameterizedTest {

	/**
	 * Perform a simple connect to the service and confirm that the connected state is reached.
	 * Also confirm that we did not get token authorization, as we did not
	 * set useTokenAuth=true.
	 */
	@Test
	public void connect() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			assertTrue("Not expecting token auth", ably.auth.getAuthMethod() == AuthMethod.basic);

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Perform a simple connect to the service
	 * and confirm that heartbeat messages are received.
	 */
	@Test
	public void connect_heartbeat() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			CompletionWaiter heartbeatWaiter = new CompletionWaiter();
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			ably.connection.ping(heartbeatWaiter);
			heartbeatWaiter.waitFor();
			assertTrue("Verify heartbeat occurred", heartbeatWaiter.success);
			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Perform a simple connect, close the connection, and verify that
	 * the connection can be re-established by calling connect().
	 */
	@Test
	public void connect_after_close() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* send a few channels to increment PendingMessageQueue.startSerial */
			for (int i = 0; i < 3; i++) {
				/* publish to the channel */
				CompletionWaiter msgComplete = new CompletionWaiter();
				ably.channels.get("test_channel").publish("test_event", "Test message", msgComplete);
				/* wait for the publish callback to be called */
				msgComplete.waitFor();
				assertTrue("Verify success callback was called", msgComplete.success);
			}

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
			try { Thread.sleep(1000L); } catch(InterruptedException e) {}

			ably.connection.connect();
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* publish to the channel in the new connection to check that it works */
			CompletionWaiter msgComplete = new CompletionWaiter();
			ably.channels.get("test_channel").publish("test_event", "Test message", msgComplete);
			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Connect with useTokenAuth=true and verify we got a token
	 */
	@Test
	public void connect_useTokenAuth() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.useTokenAuth = true;
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			assertTrue("Expected to use token auth", ably.auth.getAuthMethod() == AuthMethod.token);
			System.out.println("Token is " + ably.auth.getTokenDetails().token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init1: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}
}
