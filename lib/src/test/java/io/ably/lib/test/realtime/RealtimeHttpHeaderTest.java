package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.Log;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test for correct version headers passed to websocket
 */
public class RealtimeHttpHeaderTest {
    private static SessionHandlerNanoHTTPD server;

    @BeforeClass
    public static void setUp() throws IOException {
		/* Create custom RouterNanoHTTPD class for getting session object */
        server = new SessionHandlerNanoHTTPD(27332);
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);

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
     * Verify that correct version is used for realtime HTTP request
     */
    @Test
    public void realtime_websocket_http_header_test() {
        AblyRealtime realtime = null;
        try {
			/* Init values for local server */
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.realtimeHost = "localhost";
            opts.tls = false;

            realtime = new AblyRealtime(opts);
            Map<String, List<String>> requestParameters = null;
            for (int i=0; requestParameters==null && i<10; i++) {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                requestParameters = server.getRequestParameters();
            }

            assertNotNull("Verify connection attempt", requestParameters);
            assertEquals("Verify correct version", requestParameters.get("v"),
                    Collections.singletonList(HttpUtils.X_ABLY_VERSION_VALUE));

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

        Map<String, List<String>> getRequestParameters() {
            return requestParameters;
        }
    }

}
