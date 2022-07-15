package io.ably.core.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Locale;

import io.ably.core.rest.AblyBase;
import io.ably.core.test.common.PlatformSpecificIntegrationTest;
import io.ably.core.transport.Defaults;
import io.ably.core.types.AblyException;
import io.ably.core.types.ClientOptions;
import io.ably.core.types.ErrorInfo;
import io.ably.core.util.Log;
import io.ably.core.util.Log.LogHandler;

import org.junit.Test;

public abstract class RestInitTest extends PlatformSpecificIntegrationTest {

    /**
     * Init library with a key only
     */
    @Test
    public void init_key_string() {
        try {
            createAblyRest(testVars.keys[0].keyStr);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a null key (RSC1)
     */
    @Test
    public void init_null_key_string() {
        try {
            String key = null;
            createAblyRest(key);
            fail("init_null_key_string: Expected AblyException to be thrown when instantiating library with null key");
        } catch (AblyException e) {}
    }

    /**
     * Init library with a key in options (RSC1)
     */
    @Test
    public void init_key_opts() {
        try {
            String sampleKey = "sample:key";
            ClientOptions opts = new ClientOptions(sampleKey);
            createAblyRest(opts);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init_key_opts: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with a null key in options (RSC1)
     */
    @Test
    public void init_null_key_opts() {
        try {
            ClientOptions opts = new ClientOptions(null);
            createAblyRest(opts);
            fail("init_null_key_opts: Expected AblyException to be thrown when instantiating library with null key in options");
        } catch (AblyException e) {}
    }

    /**
     * Init library with key string
     */
    @Test
    public void init_key() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            createAblyRest(opts);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init2: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with no authentication mechanism
     * Spec: RSC1
     */
    @Test
    public void init_no_auth() {
        try {
            ClientOptions opts = new ClientOptions();
            createAblyRest(opts);
            fail("init2: Unexpected success instantiating library");
        } catch (AblyException e) {
            ErrorInfo err = e.errorInfo;
            assertEquals("Verify expected error code", err.statusCode, 400);
        }
    }

    /**
     * Init library with specified host
     */
    @Test
    public void init_host() {
        try {
            String hostExpected = "some.other.host";

            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.restHost = hostExpected;
            AblyBase ably = createAblyRest(opts);
            assertEquals("Unexpected host mismatch", hostExpected, ably.options.restHost);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init4: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with specified port
     */
    @SuppressWarnings("unused")
    @Test
    public void init_port() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.port = 9998;
            opts.tlsPort = 9999;
            AblyBase ably = createAblyRest(opts);
            assertEquals("Unexpected port mismatch", Defaults.getPort(opts), opts.tlsPort);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init5: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify tls defaults to true
     */
    @SuppressWarnings("unused")
    @Test
    public void init_default_secure() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            AblyBase ably = createAblyRest(opts);
            assertEquals("Unexpected port mismatch", Defaults.getPort(opts), Defaults.TLS_PORT);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init6: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify tls can be set to false
     */
    @SuppressWarnings("unused")
    @Test
    public void init_insecure() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.tls = false;
            AblyBase ably = createAblyRest(opts);
            assertEquals("Unexpected scheme mismatch", Defaults.getPort(opts), Defaults.PORT);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init7: Unexpected exception instantiating library");
        }
    }

    /**
     * Init with default log level
     * Spec: RSC2
     */
    @Test
    public void init_defaultLogLevel() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            assertEquals("Verify default log level is WARN", opts.logLevel, Log.WARN);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init8: Unexpected exception instantiating library");
        }
    }

    /**
     * Init with log handler; check called
     * Spec: RSC3, RSC4
     */
    private boolean init8_logCalled;
    @Test
    public void init_log_handler() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.logHandler = new LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    init8_logCalled = true;
                    System.out.println(msg);
                }
            };
            opts.logLevel = Log.VERBOSE;
            createAblyRest(opts);
            assertTrue("Log handler not called", init8_logCalled);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init8: Unexpected exception instantiating library");
        }
    }

    /**
     * Init with log handler; check not called if logLevel == NONE
     */
    private boolean init9_logCalled;
    @Test
    public void init_log_level() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.logHandler = new LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    init9_logCalled = true;
                    System.out.println(msg);
                }
            };
            opts.logLevel = Log.NONE;
            createAblyRest(opts);
            assertFalse("Log handler incorrectly called", init9_logCalled);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init9: Unexpected exception instantiating library");
        }
    }

    /**
     * Check that the logger outputs to System.out by default (RSC2)
     */
    @Test
    public void init_default_log_output_stream() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream newTarget = new PrintStream(outContent);
        PrintStream oldTarget = System.out;
        /* Log level was changed in above tests, turning in back to defaults */
        Log.setLevel(Log.defaultLevel);
        try {
            System.setOut(newTarget);

            Log.w(null, "hello");
            System.out.flush();
            String logContent = outContent.toString();
            /* \n or \r\n at the end because logs are printed with println() function */
            assertEquals("(WARN): hello" + System.lineSeparator(), logContent);
        } finally {
            System.setOut(oldTarget);
        }
    }

    /**
     * Init library with 'production' environment
     * Spec: RSC11
     */
    @Test
    public void init_production_environment() {
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.environment = "production";
            AblyBase ably = createAblyRest(opts);
            assertEquals("Unexpected host mismatch", Defaults.HOST_REST, ably.httpCore.getPrimaryHost());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init4: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with given environment
     * Spec: RSC11
     */
    @Test
    public void init_given_environment() {
        final String givenEnvironment = "staging";
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.environment = givenEnvironment;
            AblyBase ably = createAblyRest(opts);
            assertEquals("Unexpected host mismatch", String.format(Locale.ROOT, "%s-%s", givenEnvironment, Defaults.HOST_REST), ably.httpCore.getPrimaryHost());
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init4: Unexpected exception instantiating library");
        }
    }

    /**
     * Init library with given environment and specified host
     * Spec: RSC11
     */
    @Test
    public void init_given_host_environment() {
        final String givenEnvironment = "staging";
        final String specifiedHost = "fake.ably.io";
        try {
            ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
            opts.restHost = specifiedHost;
            opts.environment = givenEnvironment;
            AblyBase ably = createAblyRest(opts);
            fail("init4: Expected exception instantiating library");
            assertEquals("Unexpected host mismatch", specifiedHost, ably.options.restHost);
        } catch (AblyException e) {
            /* pass: Got exception from setting restHost and environment */
        }
    }
}
