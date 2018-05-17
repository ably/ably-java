package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.mockito.Mockito;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.ChannelEvent;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

/**
 * Created by gokhanbarisaker on 3/9/16.
 */
public class ConnectionManagerTest extends ParameterizedTest {
	/**
	 * <p>
	 * Verifies that ably connects to default host,
	 * when everything is fine.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none() throws AblyException {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.connected);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to the host without any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
		assertThat(connectionManager.getHost(), is(equalTo(opts.environment + "-realtime.ably.io")));

		ably.close();
	}

	/**
	 * <p>
	 * Verifies that fallback behaviour doesn't apply, when the default
	 * custom endpoint is being used
	 * </p>
	 * <p>
	 * Spec: RTN17b
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none_customhost() throws AblyException {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "un.reachable.host.example.com";
		opts.environment = null;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

		/* Verify that,
		 *   - connectionManager is disconnected
		 *   - connectionManager's last host did not have any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));

		ably.close();
	}

	/**
	 * <p>
	 * Verifies that the {@code ConnectionManager} first checks if an internet connection is
	 * available by issuing a GET request to https://internet-up.ably-realtime.com/is-the-internet-up.txt
	 * , when In the case of an error necessitating use of an alternative host (see RTN17d).
	 * </p>
	 * <p>
	 * Spec: RTN17c
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_none_withoutconnection() throws AblyException {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "un.reachable.host";
		opts.environment = null;
		opts.autoConnect = false;
		AblyRealtime ably = new AblyRealtime(opts);
		Connection connection = Mockito.mock(Connection.class);

		ConnectionManager connectionManager = new ConnectionManager(ably, connection) {
			@Override
			protected boolean checkConnectivity() {
				return false;
			}
		};

		connectionManager.connect();

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

		/* Verify that,
		 *   - connectionManager is disconnected
		 *   - connectionManager did not apply any fallback behavior
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));

		connectionManager.close();
	}

	/**
	 * <p>
	 * Verifies that fallback behaviour is applied and HTTP client is using same fallback
	 * endpoint, when the default realtime.ably.io endpoint is being used and has not been
	 * overriden, and a fallback is applied
	 * </p>
	 * <p>
	 * Spec: RTN17b, RTN17c, RTN17e
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void connectionmanager_fallback_applied() throws AblyException {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		// Use a host that supports fallback
		opts.realtimeHost = null;
		opts.environment = null;
		/* Use an incorrect port number for TLS. Using 80 rather than some
		 * random number (e.g. 1234) makes the failure almost immediate,
		 * instead of taking 15s to time out at each fallback host. */
		opts.tls = true;
		opts.tlsPort = 80;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

		/* Verify that,
		 *   - connectionManager is disconnected
		 *   - connectionManager's last host was a fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
		assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));

		ably.close();
	}

	/**
	 *
	 *
	 * <p>
	 * Spec: RTN17a
	 * </p>
	 */
	@Test
	public void connectionmanager_reconnect_default_endpoint() throws AblyException {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		// Use the default host, supporting fallback
		opts.realtimeHost = null;
		opts.environment = null;
		/* Use an incorrect port number for TLS. Using 80 rather than some
		 * random number (e.g. 1234) makes the failure almost immediate,
		 * instead of taking 15s to time out at each fallback host. */
		opts.tls = true;
		opts.tlsPort = 80;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		System.out.println("waiting for disconnected");
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);
		System.out.println("got disconnected");

		/* Verify that,
		 *   - connectionManager is disconnected
		 *   - connectionManager's last host was a fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
		assertThat(connectionManager.getHost(), is(not(equalTo("realtime.ably.io"))));

		/* Reconnect */
		ably.options.tlsPort = Defaults.TLS_PORT;
		System.out.println("about to connect");
		ably.connection.connect();

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is failed, because we are using an application key
		 *     that only works on sandbox;
		 *   - connectionManager first tried to connect to the original host, not a fallback one.
		 */
		System.out.println("waiting for failed");
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		System.out.println("got failed");
		assertThat(connectionManager.getHost(), is(equalTo("realtime.ably.io")));

		ably.close();
	}

	/**
	 * Test that default fallback happens with a non-default host if
	 * fallbackHostsUseDefault is set.
	 */
	@Test
	public void connectionmanager_reconnect_default_fallback() throws AblyException {
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		// Use a host that does not normally support fallback.
		opts.realtimeHost = "nondefault.ably.io";
		opts.environment = null;
		opts.fallbackHostsUseDefault = true;
		/* Use an incorrect port number for TLS. Using 80 rather than some
		 * random number (e.g. 1234) makes the failure almost immediate,
		 * instead of taking 15s to time out at each fallback host. */
		opts.tls = true;
		opts.tlsPort = 80;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		System.out.println("waiting for disconnected");
		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);
		System.out.println("got disconnected");
		ably.close();

		/* Verify that,
		 *   - connectionManager is disconnected
		 *   - connectionManager's last host was a fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
		assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));

		ably.close();
	}

	/**
	 * Connect, and then perform a close() from the calling ConnectionManager context;
	 * verify that the closed state is reached, and the connectionmanager thread has exited
	 */
	@Test
	public void close_from_connectionmanager() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			final AblyRealtime ably = new AblyRealtime(opts);
			final Thread[] threadContainer = new Thread[1];
			ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					ably.close();
					threadContainer[0] = Thread.currentThread();
				}
			});

			/* wait for cm thread to exit */
			try {
				Thread.sleep(2000L);
			} catch(InterruptedException e) {}

			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
			Thread.State cmThreadState = threadContainer[0].getState();
			assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Connect, and then perform a close() from the calling ConnectionManager context;
	 * verify that the closed state is reached, and the connectionmanager thread has exited
	 */
	@Test
	public void open_from_dedicated_thread() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.autoConnect = false;
			final AblyRealtime ably = new AblyRealtime(opts);
			final Thread[] threadContainer = new Thread[1];
			ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					threadContainer[0] = Thread.currentThread();
				}
			});

			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.submit(new Runnable() {
				public void run() {
					try {
						ably.connection.connect();
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			});

			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
			assertTrue("Not expecting token auth", ably.auth.getAuthMethod() == AuthMethod.basic);

			ably.close();
			connectionWaiter.waitFor(ConnectionState.closed);
			assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);

			/* wait for cm thread to exit */
			try {
				Thread.sleep(2000L);
			} catch(InterruptedException e) {}

			Thread.State cmThreadState = threadContainer[0].getState();
			assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Connect, and then perform a close() from the calling ConnectionManager context;
	 * verify that the closed state is reached, and the connectionmanager thread has exited
	 */
	@Test
	public void close_from_dedicated_thread() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.autoConnect = false;
			final AblyRealtime ably = new AblyRealtime(opts);
			final Thread[] threadContainer = new Thread[1];
			ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					threadContainer[0] = Thread.currentThread();
				}
			});

			ably.connection.connect();
			final ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.submit(new Runnable() {
				public void run() {
					try {
						ably.close();
						connectionWaiter.waitFor(ConnectionState.closed);
						assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);

						/* wait for cm thread to exit */
						try {
							Thread.sleep(2000L);
						} catch(InterruptedException e) {}

						Thread.State cmThreadState = threadContainer[0].getState();
						assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			});

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Connect and then verify that the connection manager has the default value for connectionStateTtl;
	 */
	@Test
	public void connection_details_has_ttl() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			final AblyRealtime ably = new AblyRealtime(opts);
			ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					try {
						Field field = ably.connection.connectionManager.getClass().getDeclaredField("connectionStateTtl");
						field.setAccessible(true);
						assertEquals("Verify connectionStateTtl has the default value", field.get(ably.connection.connectionManager), 120000L);
					} catch (NoSuchFieldException|IllegalAccessException e) {
						fail("Unexpected exception in checking connectionStateTtl");
					}
				}
			});
			new Helpers.ConnectionManagerWaiter(ably.connection.connectionManager).waitFor(ConnectionState.connected);
			ably.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * RTN15g1, RTN15g2. Connect, disconnect, reconnect after (ttl + idle interval) period has passed,
	 * check that the connection is a new one;
	 */
	@Test
	public void connection_has_new_id_when_reconnecting_after_statettl_plus_idleinterval_has_passed() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.realtimeRequestTimeout = 2000L;
			final AblyRealtime ably = new AblyRealtime(opts);
			final long newTtl = 1000L;
			final long newIdleInterval = 1000L;
			/* We want this greater than newTtl + newIdleInterval */
			final long waitInDisconnectedState = 3000L;

			ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					try {
						Field connectionStateField = ably.connection.connectionManager.getClass().getDeclaredField("connectionStateTtl");
						connectionStateField.setAccessible(true);
						connectionStateField.setLong(ably.connection.connectionManager, newTtl);
						Field maxIdleField = ably.connection.connectionManager.getClass().getDeclaredField("maxIdleInterval");
						maxIdleField.setAccessible(true);
						maxIdleField.setLong(ably.connection.connectionManager, newIdleInterval);
					} catch (NoSuchFieldException|IllegalAccessException e) {
						fail("Unexpected exception in checking connectionStateTtl");
					}
				}
			});

			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			final String firstConnectionId = ably.connection.id;

			ably.connection.once(ConnectionEvent.disconnected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					synchronized (ably.connection.connectionManager) {
						try {
							/* The client will try to reconnect right away after disconnection.
							 * We want it to stay into a disconnected state long enough
							 * so that the connection becomes stale.
							 */
							ably.connection.connectionManager.wait(waitInDisconnectedState);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			});

			ably.connection.connectionManager.requestState(ConnectionState.disconnected);
			connectionWaiter.waitFor(ConnectionState.disconnected);

			ably.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					assertEquals("Client has reconnected", ConnectionState.connected, state.current);
					String secondConnectionId = ably.connection.id;
					assertNotNull(secondConnectionId);
					assertNotEquals("connection has a different id", firstConnectionId, secondConnectionId);
					ably.close();
				}
			});

			connectionWaiter.waitFor(ConnectionState.closed);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * RTN15g1, RTN15g2. Connect, disconnect, reconnect before (ttl + idle interval) period has passed,
	 * check that the connection is the same;
	 */
	@Test
	public void connection_has_same_id_when_reconnecting_before_statettl_plus_idleinterval_has_passed() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			final AblyRealtime ably = new AblyRealtime(opts);

			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			String firstConnectionId = ably.connection.id;
			ably.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* Wait for a connected state after the disconnection triggered above */
			connectionWaiter.waitFor(ConnectionState.connected);

			String secondConnectionId = ably.connection.id;
			assertNotNull(secondConnectionId);
			assertEquals("connection has the same id", firstConnectionId, secondConnectionId);
			ably.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * RTN15g3. Connect, attach some channels, disconnect, reconnect after (ttl + idle interval) period has passed,
	 * check that the client reconnects with a different connection and that the channels attached during the first
	 * connection are correctly reattached;
	 */
	@Test
	public void channels_are_reattached_after_reconnecting_when_statettl_plus_idleinterval_has_passed() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			final AblyRealtime ably = new AblyRealtime(opts);
			final long newTtl = 1000L;
			final long newIdleInterval = 1000L;
			/* We want this greater than newTtl + newIdleInterval */
			final long waitInDisconnectedState = 3000L;
			final String channelName = "test-reattach-after-ttl";
			ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					try {
						Field connectionStateField = ably.connection.connectionManager.getClass().getDeclaredField("connectionStateTtl");
						connectionStateField.setAccessible(true);
						connectionStateField.setLong(ably.connection.connectionManager, newTtl);
						Field maxIdleField = ably.connection.connectionManager.getClass().getDeclaredField("maxIdleInterval");
						maxIdleField.setAccessible(true);
						maxIdleField.setLong(ably.connection.connectionManager, newIdleInterval);
					} catch (NoSuchFieldException|IllegalAccessException e) {
						fail("Unexpected exception in checking connectionStateTtl");
					}
				}
			});

			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			final String firstConnectionId = ably.connection.id;

			/* Attach to channel */
			final Channel channel = ably.channels.get(channelName);
			channel.once(ChannelState.attached, new ChannelStateListener() {
				@Override
				public void onChannelStateChanged(ChannelStateChange stateChange) {
					System.out.println("Channel attached for the first time");
					assertEquals("Channel was not attached", stateChange.current, ChannelState.attached);
				}
			});

			/* attach and wait for the channel to be attached */
			channel.attach();
			ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(channel);
			channelWaiter.waitFor(ChannelState.attached);

			/* suppress automatic retries by the connection manager */
			try {
				Method method = ably.connection.connectionManager.getClass().getDeclaredMethod("disconnectAndSuppressRetries");
				method.setAccessible(true);
				method.invoke(ably.connection.connectionManager);
			} catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException e) {
				fail("Unexpected exception in suppressing retries");
			}

			connectionWaiter.waitFor(ConnectionState.disconnected);
			assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

			/* Wait for the connection to go stale, then reconnect */
			try { Thread.sleep(waitInDisconnectedState); } catch(InterruptedException e) {}
			ably.connection.connect();
			connectionWaiter.waitFor(ConnectionState.connected);

			/* Verify the connection is new and that channel is reattached with resumed false. Then close. */
			channel.once(ChannelEvent.attached, new ChannelStateListener() {
				@Override
				public void onChannelStateChanged(ChannelStateChange stateChange) {
					System.out.println("Channel reattached after a new connection has been established");
					String secondConnectionId = ably.connection.id;
					assertNotNull(secondConnectionId);
					assertNotEquals("Connection has the same id", firstConnectionId, secondConnectionId);
					assertEquals("Resumed is true and should be false", stateChange.resumed, false);
					ably.close();
				}
			});
			(new Helpers.ConnectionWaiter(ably.connection)).waitFor(ConnectionState.closed);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}
}
