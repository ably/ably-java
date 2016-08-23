package io.ably.lib.http;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Channel;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

/**
 * Created by VOstopolets on 8/17/16.
 */
public class HttpHeaderTest {

    private static SessionHandlerNanoHTTPD server;

    @BeforeClass
    public static void setUp() throws IOException {
        /* Create custom RouterNanoHTTPD class for getting session object */
        server = new SessionHandlerNanoHTTPD(27331);
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
     * The header X-Ably-Lib: [lib][.optional variant]?-[version]
     * should be included in all REST requests to the Ably endpoint
     * see {@link io.ably.lib.http.HttpUtils#X_ABLY_LIB_VALUE}
     * <p>
     * Spec: RSC7b
     * </p>
     *
     */
    @Test
    public void header_lib_channel_publish() {
        try {
            /* Init values for local server */
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
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
            String ably_lib_header = HttpUtils.X_ABLY_LIB_HEADER.toLowerCase();

            /* Check header */
            Assert.assertNotNull("Expected headers", headers);
            Assert.assertTrue(String.format("Expected header %s", HttpUtils.X_ABLY_LIB_HEADER), headers.containsKey(ably_lib_header));
            Assert.assertEquals(headers.get(ably_lib_header), HttpUtils.X_ABLY_LIB_VALUE);
        } catch (AblyException e) {
            e.printStackTrace();
            Assert.fail("header_lib_channel_publish: Unexpected exception");
        }
    }

    private static class SessionHandlerNanoHTTPD extends RouterNanoHTTPD {
        public Map<String, String> headers;

        public SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            headers = new HashMap<>(session.getHeaders());
            return newFixedLengthResponse("Ignored response");
        }

        public Map<String, String> getHeaders() {
            return headers;
        }
    }
}
