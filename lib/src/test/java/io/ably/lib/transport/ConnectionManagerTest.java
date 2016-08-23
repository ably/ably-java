package io.ably.lib.transport;

import org.junit.Test;
import org.mockito.Mockito;

import io.ably.lib.http.HttpHeaderTest;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Param;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Created by gokhanbarisaker on 3/9/16.
 */
public class ConnectionManagerTest {
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
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.connected);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to the host without any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
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
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "un.reachable.host.example.com";
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to the host without any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
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
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.realtimeHost = "un.reachable.host";
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

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is failed
		 *   - connectionManager is didn't applied any fallback behavior
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
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
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		// Use a host that supports fallback
		opts.realtimeHost = Defaults.HOST_REALTIME;
		// Use a non-reachable port number
		opts.tls = true;
		opts.tlsPort = 1234;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to a fallback host
		 *   - Ably http client is also using the same fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));
		assertThat(ably.http.getHost(), is(equalTo(connectionManager.getHost())));
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
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		// Use a host that supports fallback
		opts.realtimeHost = Defaults.HOST_REALTIME;
		// Use a non-reachable port number
		opts.tls = true;
		opts.tlsPort = 1234;
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.failed);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to a fallback host
		 *   - Ably http client is also using the same fallback host
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.failed));
		assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));
		assertThat(ably.http.getHost(), is(equalTo(connectionManager.getHost())));

		/* Reconnect */
		ably.options.tlsPort = Defaults.TLS_PORT;
		ably.connection.connect();

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.connected);

		/* Verify that,
		 *   - connectionManager is connected
		 *   - connectionManager is connected to the host without any fallback
		 */
		assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
		assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
	}

	/**
	 * <p>
	 * Library and version param 'lib' should include the header value described there
	 * {@link io.ably.lib.http.HttpUtils#X_ABLY_LIB_VALUE},
	 * see {@link HttpHeaderTest#header_lib_channel_publish()}
	 * </p>
	 * <p>
	 * Spec: RTN2g
	 * </p>
	 */
	@Test
	public void connectionmanager_param_lib() throws AblyException {
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.connected);

		/* Get X-Ably-Lib param */
		Param[] params = connectionManager.getConnectParams();
		Param ablyLibParam = Param.getParamByKey(params, ITransport.TransportParams.LIB_PARAM_KEY);

		/* Verify that,
		 *   - X-Ably-Lib header exists
		 *   - X-Ably-Lib header value equals correct static value
		 */
		assertNotNull("Expected X-Ably-Lib header", ablyLibParam);
		assertEquals(ablyLibParam.value, HttpUtils.X_ABLY_LIB_VALUE);
	}
}
