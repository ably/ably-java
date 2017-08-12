package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;

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

	/**
	 * Verify that given transport params are included in the ws connection URL.
	 */
	@Test
	public void connect_with_transport_params() {
		try {
			DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(opts);
			final String[] urlWrapper = new String[1];
			opts.protocolListener = new RawProtocolListener() {
				@Override
				public void onRawConnect(String url) {
					/* store url */
					urlWrapper[0] = url;
				}
				@Override
				public void onRawMessageSend(ProtocolMessage message) {}
				@Override
				public void onRawMessageRecv(ProtocolMessage message) {}
			};
			opts.transportParams = new Param[] {new Param("testStringParam", "testStringValue"), new Param("testIntParam", 100), new Param("testBooleanParam", false)};
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			String url = urlWrapper[0];
			assertNotNull("Verify connection url was obtained", url);
			assertTrue("Verify expected string param present", url.contains("testStringParam=testStringValue"));
			assertTrue("Verify expected int param present", url.contains("testIntParam=100"));
			assertTrue("Verify expected boolean param present", url.contains("testBooleanParam=false"));

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("connect_with_transport_params: Unexpected exception instantiating library");
		}
	}

	/**
	 * Initiate a connect using AblyRealtime.connect()
	 */
	@Test
	public void realtime_connect_proxy() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.autoConnect = false;
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			/* verify no connection happens */
			assertFalse("Verify no connection happens", connectionWaiter.waitFor(ConnectionState.connected, 1, 1000L));

			/* trigger connection */
			ably.connect();
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("realtime_connect_proxy: Unexpected exception instantiating library");
		}
	}

}
