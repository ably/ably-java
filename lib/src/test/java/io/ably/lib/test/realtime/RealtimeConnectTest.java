package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.Log;

import org.junit.Test;

public class RealtimeConnectTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Perform a simple connect to the service using the default (binary)
	 * protocol and confirm that the connected state is reached.
	 * Also confirm that we did not get token authorization, as we did not
	 * set useTokenAuth=true.
	 */
	@Test
	public void connect_binary() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			assertTrue("Not expecting token auth", ably.auth.getTokenAuth() == null);

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Perform a simple connect to the service using the text
	 * protocol and confirm that the connected state is reached.
	 */
	@Test
	public void connect_text() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			AblyRealtime ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Perform a simple connect to the service with the binary protocol
	 * and confirm that heartbeat messages are received.
	 */
	@Test
	public void connect_heartbeat_binary() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			testVars.fillInOptions(opts);
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
	 * Perform a simple connect to the service with the text protocol
	 * and confirm that heartbeat messages are received.
	 */
	@Test
	public void connect_heartbeat_text() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			testVars.fillInOptions(opts);
			opts.useBinaryProtocol = false;
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
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
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
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useTokenAuth = true;
			ably = new AblyRealtime(opts);
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			assertTrue("Expected to use token auth", ably.auth.getTokenAuth() != null);
			System.out.println("Token is " + ably.auth.getTokenAuth().getTokenDetails().token);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init1: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}
}
