package io.ably.lib.http;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.util.StatusHandler;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Param;

import static org.mockito.Mockito.mock;

/**
 * Created by VOstopolets on 8/17/16.
 */
public class HttpHeaderTest {

    private static SessionHandlerNanoHTTPD server;

    @BeforeClass
    public static void setUp() throws IOException {
        // create custom RouterNanoHTTPD class for getting session object
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
     * should be included in all REST requests to the Ably endpoint see {@link io.ably.lib.http.HttpUtils}
     * <p>
     * Spec: RSC7b
     */
    @Test
    public void header_lib_general() throws MalformedURLException {
        try {
            // init values for local server
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            AblyRest ably = new AblyRest(opts);
            Http.RequestBody requestBody = new Http.ByteArrayRequestBody(new byte[0], NanoHTTPD.MIME_PLAINTEXT);

            // init URL
            URL url = new URL("http://localhost:" + server.getListeningPort());
            try {
                ably.http.httpExecuteWithRetry(
                        url, /* Ignore */
                        Http.GET, /* Ignore */
                        new Param[0], /* Ignore */
                        requestBody, /* Ignore */
                        mock(Http.ResponseHandler.class), /* Ignore */
                        true
                );
            } catch (AblyException ex) {
            }

            // get last session
            NanoHTTPD.IHTTPSession session = server.getResult();
            Map<String, String> headers = session.getHeaders();

            // prepare checked header
            String ably_lib_header = HttpUtils.X_ABLY_LIB_HEADER.toLowerCase();

            // check header
            Assert.assertNotNull("Expected headers", headers);
            Assert.assertTrue(String.format("Expected header %s", HttpUtils.X_ABLY_LIB_HEADER), headers.containsKey(ably_lib_header));
            Assert.assertEquals(headers.get(ably_lib_header), HttpUtils.getAblyLibValue());
        } catch (AblyException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void header_lib_async() {
        try {
            // init values for local server
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            AblyRest ably = new AblyRest(opts);

            // create waiter callback
            Helpers.AsyncWaiter callback = new Helpers.AsyncWaiter();

            // send async request
            ably.asyncHttp.get(
                    "", /* Ignore */
                    new Param[0], /* Ignore */
                    new Param[0], /* Ignore */
                    mock(Http.ResponseHandler.class), /* Ignore */
                    callback /* Set waiter Callback */
            );
            // waiting while request is completed
            callback.waitFor();

            // get last session
            NanoHTTPD.IHTTPSession session = server.getResult();
            Map<String, String> headers = session.getHeaders();

            // prepare checked header
            String ably_lib_header = HttpUtils.X_ABLY_LIB_HEADER.toLowerCase();

            // check header
            Assert.assertNotNull("Expected headers", headers);
            Assert.assertTrue(String.format("Expected header %s", HttpUtils.X_ABLY_LIB_HEADER), headers.containsKey(ably_lib_header));
            Assert.assertEquals(headers.get(ably_lib_header), HttpUtils.getAblyLibValue());
        } catch (AblyException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void header_lib_http() throws MalformedURLException {
        try {
            // init values for local server
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            opts.port = server.getListeningPort();
            opts.restHost = "localhost";
            AblyRest ably = new AblyRest(opts);
            Http.RequestBody requestBody = new Http.ByteArrayRequestBody(new byte[0], NanoHTTPD.MIME_PLAINTEXT);

            try {
                ably.http.ablyHttpExecute(
                        "", /* Ignore */
                        Http.GET, /* Ignore */
                        new Param[0], /* Ignore */
                        new Param[0], /* Ignore */
                        requestBody, /* Ignore */
                        mock(Http.ResponseHandler.class) /* Ignore */
                );
            } catch (AblyException ex) {
            }

            // get last session
            NanoHTTPD.IHTTPSession session = server.getResult();
            Map<String, String> headers = session.getHeaders();

            // prepare checked header
            String ably_lib_header = HttpUtils.X_ABLY_LIB_HEADER.toLowerCase();

            // check header
            Assert.assertNotNull("Expected headers", headers);
            Assert.assertTrue(String.format("Expected header %s", HttpUtils.X_ABLY_LIB_HEADER), headers.containsKey(ably_lib_header));
            Assert.assertEquals(headers.get(ably_lib_header), HttpUtils.getAblyLibValue());
        } catch (AblyException e) {
            e.printStackTrace();
        }
    }

    private static class SessionHandlerNanoHTTPD extends RouterNanoHTTPD {
        public NanoHTTPD.IHTTPSession result;

        public SessionHandlerNanoHTTPD(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            result = session;
            return super.serve(session);
        }

        public IHTTPSession getResult() {
            return result;
        }
    }
}
