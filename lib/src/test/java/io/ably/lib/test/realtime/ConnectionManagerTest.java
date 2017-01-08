package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.mockito.Mockito;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
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

		/* Verify that,
		 *   - connectionManager is disconnected
		 *   - connectionManager's last host was a fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
		assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));
	}
}
