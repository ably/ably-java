package io.ably.lib.test.realtime;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ConnectivityStateChangeEmitter;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;

import static org.junit.Assert.assertEquals;

/**
 * Created by gaker on 4/30/16.
 */
public class RealtimeConnectivityStateTest {
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Verify that the connection enters the disconnected state, after connection manager
	 * receives a notification about client loosed connectivity.
	 */
	@Test
	public void test_connectivity_change() throws AblyException {
		AblyRealtime ably = null;
		try {
			Setup.TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			ably.connection.on(new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					System.err.println("State changed from: " + state.previous.name() + "to: " + state.current.name());
				}
			});

			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);

			ably.connection.connectionManager.onConnectivityStateChanged(false);
			connectionWaiter.waitFor(ConnectionState.disconnected);
			connectionWaiter.waitFor(ConnectionState.connected);
		} finally {
			if (ably != null) {
				ably.close();
			}
		}
	}
}
