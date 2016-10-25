package io.ably.lib.test.realtime;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoWSD;
import io.ably.lib.test.rest.HttpHeaderTest;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ITransport;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by VOstopolets on 8/25/16.
 */
public class ConnectionParamTest {

	private static ParamHandlerNanoWsd server;

	private static class ParamHandlerNanoWsd extends NanoWSD {

		private Map<String, String> params;

		public ParamHandlerNanoWsd(String hostname, int port) {
			super(hostname, port);
		}

		public Map<String, String> getParams() {
			return params;
		}

		@Override
		protected WebSocket openWebSocket(IHTTPSession handshake) {
			params = new HashMap<>(handshake.getParms());
			return new WebSocket(handshake) {
				@Override
				protected void onOpen() {
					closeAllConnections();
				}

				@Override
				protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
				}

				@Override
				protected void onMessage(WebSocketFrame message) {
				}

				@Override
				protected void onPong(WebSocketFrame pong) {
				}

				@Override
				protected void onException(IOException exception) {
				}
			};
		}
	}

	@BeforeClass
	public static void setUp() throws IOException {
		server = new ParamHandlerNanoWsd("localhost", 27331);
		server.start();
	}

	@AfterClass
	public static void tearDown() {
		server.stop();
	}

	/**
	 * <p>
	 * Library and version param 'lib' should include the header value described there
	 * {@link HttpUtils#X_ABLY_LIB_VALUE},
	 * see {@link HttpHeaderTest#header_lib_channel_publish()}
	 * </p>
	 * <p>
	 * Spec: RTN2g
	 * </p>
	 */
	@Test
	public void connectionmanager_param_lib() throws AblyException {
		/* Init values for local server */
		Setup.TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		opts.tls = false;
		opts.realtimeHost = "localhost";
		opts.environment = null;
		opts.port = server.getListeningPort();

		AblyRealtime ably = new AblyRealtime(opts);
		ConnectionManager connectionManager = ably.connection.connectionManager;

		new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

		/* Get params */
		Map<String, String> params = server.getParams();

		/* Verify that params exists */
		assertNotNull("Expected params", params);

		/* Get Lib param */
		String ablyLibParam = params.get(ITransport.TransportParams.LIB_PARAM_KEY);

		/* Verify that,
		 *   - Lib param exists
		 *   - Lib param value equals correct static value
		 */
		assertNotNull("Expected Lib param", ablyLibParam);
		assertEquals(ablyLibParam, HttpUtils.X_ABLY_LIB_VALUE);
	}
}
