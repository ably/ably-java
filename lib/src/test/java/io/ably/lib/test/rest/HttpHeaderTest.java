package io.ably.lib.test.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import fi.iki.elonen.NanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

/**
 * Created by VOstopolets on 8/17/16.
 */
public class HttpHeaderTest extends ParameterizedTest {

	private static SessionHandlerNanoHTTPD server;

	@BeforeClass
	public static void setUp() throws IOException {
		/* Create custom RouterNanoHTTPD class for getting session object */
		server = new SessionHandlerNanoHTTPD(27331);
		server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

		/* wait for server to start */
		while (!server.wasStarted()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@AfterClass
	public static void tearDown() {
		server.stop();
	}

	/**
	 * The header X-Ably-Lib: [lib][.optional variant]?-[version]
	 * should be included in all REST requests to the Ably endpoint
	 * see {@link io.ably.lib.http.HttpUtils#ABLY_LIB_VERSION}
	 * <p>
	 * Spec: RSC7b, G4
	 * </p>
	 *
	 * Spec: RSC7a: Must have the header X-Ably-Version: 1.0 (or whatever the
	 * spec version is).
	 */
	@Test
	public void header_lib_channel_publish() {
		try {
			/* Init values for local server */
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			opts.environment = null;
			opts.tls = false;
			opts.port = server.getListeningPort();
			opts.restHost = "localhost";
			AblyRest ably = new AblyRest(opts);

			/* Publish message */
			String messageName = "test message";
			String messageData = String.valueOf(System.currentTimeMillis());

			Channel channel = ably.channels.get("test");
			channel.publish(messageName, messageData);

			/* Get last headers */
			Map<String, String> headers = server.getHeaders();

			/* Prepare checked header */
			String ably_version_header = Defaults.ABLY_VERSION_HEADER.toLowerCase();
			String ably_lib_header = Defaults.ABLY_LIB_HEADER.toLowerCase();

			/* Check header */
			Assert.assertNotNull("Expected headers", headers);
			Assert.assertTrue(String.format("Expected header %s", Defaults.ABLY_VERSION_HEADER), headers.containsKey(ably_version_header));
			Assert.assertEquals(headers.get(ably_version_header), Defaults.ABLY_VERSION);
			Assert.assertTrue(String.format("Expected header %s", Defaults.ABLY_LIB_HEADER), headers.containsKey(ably_lib_header));
			Assert.assertEquals(headers.get(ably_lib_header), Defaults.ABLY_LIB_VERSION);
		} catch (AblyException e) {
			e.printStackTrace();
			Assert.fail("header_lib_channel_publish: Unexpected exception");
		}
	}

	private static class SessionHandlerNanoHTTPD extends NanoHTTPD {
		Map<String, String> requestHeaders;

		public SessionHandlerNanoHTTPD(int port) {
			super(port);
		}

		@Override
		public Response serve(IHTTPSession session) {
			requestHeaders = new HashMap<>(session.getHeaders());
			int contentLength = Integer.parseInt(requestHeaders.get("content-length"));
			try {
				session.getInputStream().read(new byte[contentLength]);
			} catch (IOException e) {}
			return newFixedLengthResponse("Ignored response");
		}

		public Map<String, String> getHeaders() {
			return requestHeaders;
		}
	}
}
