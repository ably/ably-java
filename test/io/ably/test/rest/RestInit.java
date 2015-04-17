package io.ably.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.rest.AblyRest;
import io.ably.test.rest.RestSetup.TestVars;
import io.ably.transport.Defaults;
import io.ably.types.AblyException;
import io.ably.types.ClientOptions;
import io.ably.util.Log;
import io.ably.util.Log.LogHandler;

import org.junit.Test;

public class RestInit {

	/**
	 * Init library with a key only
	 */
	@Test
	public void init_key_string() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			new AblyRest(testVars.keys[0].keyStr);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with a key in options
	 */
	@Test
	public void init_key_opts() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			new AblyRest(new ClientOptions(testVars.keys[0].keyStr));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init1: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with key string
	 */
	@Test
	public void init_key() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			new AblyRest(opts);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init2: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init library with specified host
	 */
	@SuppressWarnings("unused")
	@Test
	public void init_host() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.host = "some.other.host";
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected host mismatch", Defaults.getHost(opts), opts.host);
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.port = 9998;
			opts.tlsPort = 9999;
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected port mismatch", Defaults.getPort(opts), opts.tlsPort);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init5: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify encrypted defaults to true
	 */
	@SuppressWarnings("unused")
	@Test
	public void init_default_secure() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected port mismatch", Defaults.getPort(opts), Defaults.TLS_PORT);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init6: Unexpected exception instantiating library");
		}
	}

	/**
	 * Verify encrypted can be set to false
	 */
	@SuppressWarnings("unused")
	@Test
	public void init_insecure() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.tls = false;
			AblyRest ably = new AblyRest(opts);
			assertEquals("Unexpected scheme mismatch", Defaults.getPort(opts), Defaults.PORT);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init7: Unexpected exception instantiating library");
		}
	}

	/**
	 * Init with log handler; check called
	 */
	private boolean init8_logCalled;
	@Test
	public void init_log_handler() {
		try {
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.logHandler = new LogHandler() {
				@Override
				public void println(int severity, String tag, String msg, Throwable tr) {
					init8_logCalled = true;
					System.out.println(msg);
				}
			};
			opts.logLevel = Log.VERBOSE;
			new AblyRest(opts);
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
			TestVars testVars = RestSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.logHandler = new LogHandler() {
				@Override
				public void println(int severity, String tag, String msg, Throwable tr) {
					init9_logCalled = true;
					System.out.println(msg);
				}
			};
			opts.logLevel = Log.NONE;
			new AblyRest(opts);
			assertFalse("Log handler incorrectly called", init9_logCalled);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init9: Unexpected exception instantiating library");
		}
	}
}
