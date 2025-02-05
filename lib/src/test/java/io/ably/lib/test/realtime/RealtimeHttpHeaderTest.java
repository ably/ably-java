package io.ably.lib.test.realtime;

import fi.iki.elonen.NanoHTTPD;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.DerivedClientOptions;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

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

            /* Spec RTN2f
             * This test should not directly validate version against Defaults.ABLY_VERSION, nor
             * Defaults.ABLY_VERSION_PARAM, as ultimately the request param has been derived from those values.
             */
            assertEquals("Verify correct version", requestParameters.get("v"),
                    Collections.singletonList("2"));

            /* Spec RSC7d3
             * This test should not directly validate version against Defaults.ABLY_AGENT_VERSION, nor
             * Defaults.ABLY_AGENT_PARAM, as ultimately the request param has been derived from those values.
             */
            assertEquals("Verify correct lib version", requestParameters.get("agent"),
                    Collections.singletonList("ably-java/1.2.48 jre/" + System.getProperty("java.version")));

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

    /**
     * Verify that correct version is used for realtime HTTP request
     */
    @Test
    public void realtime_derived_client_headers_test() throws InterruptedException, AblyException {
        AblyRealtime realtime;
        /* Init values for local server */
        String key = testVars.keys[0].keyStr;
        ClientOptions opts = new ClientOptions(key);
        opts.port = port;
        opts.realtimeHost = "localhost";
        opts.restHost = "localhost";
        opts.tls = false;
        opts.useBinaryProtocol = testParams.useBinaryProtocol;
        opts.autoConnect = false;

        server.resetRequestParameters();

        realtime = new AblyRealtime(opts);

        server.resetRequestHeaders();
        DerivedClientOptions derivedOptions = DerivedClientOptions.builder().addAgent("chat-android", "0.1.2").build();
        AblyRealtime derivedRealtime = realtime.createDerivedClient(derivedOptions);
        try { derivedRealtime.time(); } catch (Exception e) {}
        Map<String, String> requestHeaders = tryGetServerRequestHeaders();

        assertEquals("Verify correct lib version",
            "ably-java/1.2.48 jre/" + System.getProperty("java.version") + " chat-android/0.1.2",
            requestHeaders.get("ably-agent")
        );

        try { realtime.time(); } catch (Exception e) {}

        requestHeaders = tryGetServerRequestHeaders();

        assertEquals("Verify correct lib version",
            "ably-java/1.2.48 jre/" + System.getProperty("java.version"),
            requestHeaders.get("ably-agent")
        );

        assertSame(realtime.connection, derivedRealtime.connection);
        assertSame(realtime.channels, derivedRealtime.channels);
    }

    private Map<String, String> tryGetServerRequestHeaders() throws InterruptedException {
        Map<String, String> requestHeaders = null;

        for (int i = 0; requestHeaders == null && i < 10; i++) {
            Thread.sleep(100);
            requestHeaders = server.getRequestHeaders();
        }

        assertNotNull("Verify connection attempt", requestHeaders);

        return requestHeaders;
    }

    private static class SessionHandlerNanoHTTPD extends NanoHTTPD {
        Map<String, List<String>> requestParameters;
        Map<String, String> requestHeaders;

        SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (requestParameters == null)
                requestParameters = decodeParameters(session.getQueryParameterString());
            if (requestHeaders == null)
                requestHeaders = session.getHeaders();

            return newFixedLengthResponse("Ignored response");
        }

        void resetRequestParameters() {
            requestParameters = null;
        }

        void resetRequestHeaders() {
            requestHeaders = null;
        }

        Map<String, List<String>> getRequestParameters() {
            return requestParameters;
        }

        Map<String, String> getRequestHeaders() {
            return requestHeaders;
        }
    }

}

