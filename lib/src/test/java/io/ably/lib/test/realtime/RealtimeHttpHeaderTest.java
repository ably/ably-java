package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

/**
 * Test for correct version headers passed to websocket
 */
public class RealtimeHttpHeaderTest extends ParameterizedTest {
	private SessionHandlerNanoHTTPD server;
	private int port;

	@Before
	public void setUp() throws IOException {
		/* Create custom RouterNanoHTTPD class for getting session object */
		port = testParams.useBinaryProtocol ? 27333 : 27332;
		server = new SessionHandlerNanoHTTPD(port);
		server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

		while (!server.wasStarted()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@After
	public void tearDown() {
		server.stop();
	}

	/**
	 * Verify that correct version is used for realtime HTTP request
	 */
	@Test
	public void realtime_websocket_param_test() {
		AblyRealtime realtime = null;
		try {
			/* Init values for local server */
			String key = testVars.keys[0].keyStr;
			ClientOptions opts = new ClientOptions(key);
			opts.port = port;
			opts.realtimeHost = "localhost";
			opts.tls = false;
			opts.useBinaryProtocol = testParams.useBinaryProtocol;

			server.resetRequestParameters();
			realtime = new AblyRealtime(opts);
			Map<String, List<String>> requestParameters = null;
			for (int i = 0; requestParameters == null && i<10; i++) {
				try { Thread.sleep(100); } catch (InterruptedException e) {}
				requestParameters = server.getRequestParameters();
			}
			realtime.close();

			assertNotNull("Verify connection attempt", requestParameters);

			/* Spec RTN2e */
			assertEquals("Verify correct key param", requestParameters.get("key"),
					Collections.singletonList(key));

			/* Spec RTN2f */
			assertEquals("Verify correct version", requestParameters.get(Defaults.ABLY_VERSION_PARAM),
					Collections.singletonList(Defaults.ABLY_VERSION));

			/* Spec RTN2g */
			assertEquals("Verify correct lib version", requestParameters.get(Defaults.ABLY_LIB_PARAM),
					Collections.singletonList(Defaults.ABLY_LIB_VERSION));

			/* Spec RTN2a */
			assertEquals("Verify correct format", requestParameters.get("format"),
					Collections.singletonList(testParams.useBinaryProtocol ? "msgpack" : "json"));

			/* test echo option */
			opts = new ClientOptions(key);
			opts.port = port;
			opts.realtimeHost = "localhost";
			opts.tls = false;
			opts.useBinaryProtocol = testParams.useBinaryProtocol;
			opts.echoMessages = false;
			server.resetRequestParameters();
			realtime = new AblyRealtime(opts);
			requestParameters = null;
			for (int i = 0; requestParameters == null && i<10; i++) {
				try { Thread.sleep(100); } catch (InterruptedException e) {}
				requestParameters = server.getRequestParameters();
			}
			realtime.close();

			assertNotNull("Verify connection attempt", requestParameters);

			/* Spec: RTN2b */
			assertEquals("Verify correct echo param", requestParameters.get("echo"),
					Collections.singletonList("false"));

			/* test token auth option */
			String clientId = "test client id";
			opts = new ClientOptions();
			opts.port = port;
			opts.realtimeHost = "localhost";
			opts.tls = false;
			opts.useBinaryProtocol = testParams.useBinaryProtocol;
			opts.useTokenAuth = true;
			opts.token = key; /* not really a token, but ok for this test */
			opts.clientId = clientId;

			server.resetRequestParameters();
			realtime = new AblyRealtime(opts);
			requestParameters = null;
			for (int i = 0; requestParameters == null && i<10; i++) {
				try { Thread.sleep(100); } catch (InterruptedException e) {}
				requestParameters = server.getRequestParameters();
			}
			realtime.close();

			assertNotNull("Verify connection attempt", requestParameters);

			/* Spec: RTN2d */
			assertEquals("Verify correct clientId param", requestParameters.get("clientId"),
					Collections.singletonList(clientId));

			/* Spec: RTN2e */
			assertEquals("Verify correct accessToken param", requestParameters.get("accessToken"),
					Collections.singletonList(key));

		} catch (AblyException e) {
			e.printStackTrace();
			Assert.fail("websocket_http_header_test: Unexpected exception");
		} finally {
			if (realtime != null)
				realtime.close();
		}
	}

	private static class SessionHandlerNanoHTTPD extends RouterNanoHTTPD {
		Map<String, List<String>> requestParameters;

		SessionHandlerNanoHTTPD(int port) {
			super(port);
		}

		@Override
		public Response serve(IHTTPSession session) {
			if (requestParameters == null)
				requestParameters = decodeParameters(session.getQueryParameterString());
			return newFixedLengthResponse("Ignored response");
		}

		void resetRequestParameters() {
			requestParameters = null;
		}

		Map<String, List<String>> getRequestParameters() {
			return requestParameters;
		}
	}

}

