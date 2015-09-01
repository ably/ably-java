package io.ably.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.realtime.AblyRealtime;
import io.ably.test.realtime.RealtimeSetup.TestVars;
import io.ably.transport.Defaults;
import io.ably.types.AblyException;
import io.ably.types.ClientOptions;
import io.ably.util.Log;
import io.ably.util.Log.LogHandler;

import org.junit.Test;

public class RealtimeInit {

	/**
	 * Init library with a key only
	 */
	@Test
	public void init_key_string() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ably = new AblyRealtime(testVars.keys[0].keyStr);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Init library with a key in options
	 */
	@Test
	public void init_key_opts() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ably = new AblyRealtime(new ClientOptions(testVars.keys[0].keyStr));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init1: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Init library with key string
	 */
	@Test
	public void init_key() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init2: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Init library with specified host
	 */
	@Test
	public void init_host() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.restHost = "some.other.host";
			ably = new AblyRealtime(opts);
			assertEquals("Unexpected host mismatch", Defaults.getHost(opts), opts.restHost);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init4: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Init library with specified port
	 */
	@Test
	public void init_port() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.port = 9998;
			opts.tlsPort = 9999;
			ably = new AblyRealtime(opts);
			assertEquals("Unexpected port mismatch", Defaults.getPort(opts), opts.tlsPort);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init5: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Verify encrypted defaults to true
	 */
	@Test
	public void init_default_secure() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			assertEquals("Unexpected port mismatch", Defaults.getPort(opts), Defaults.TLS_PORT);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init6: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Verify encrypted can be set to false
	 */
	@Test
	public void init_insecure() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.tls = false;
			ably = new AblyRealtime(opts);
			assertEquals("Unexpected scheme mismatch", Defaults.getPort(opts), Defaults.PORT);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init7: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Init with log handler; check called
	 */
	private boolean init8_logCalled;
	@Test
	public void init_log_handler() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.logHandler = new LogHandler() {
				@Override
				public void println(int severity, String tag, String msg, Throwable tr) {
					init8_logCalled = true;
					System.out.println(msg);
				}
			};
			opts.logLevel = Log.VERBOSE;
			ably = new AblyRealtime(opts);
			assertTrue("Log handler not called", init8_logCalled);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init8: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}

	/**
	 * Init with log handler; check not called if logLevel == NONE
	 */
	private boolean init9_logCalled;
	@Test
	public void init_log_level() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = RealtimeSetup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.logHandler = new LogHandler() {
				@Override
				public void println(int severity, String tag, String msg, Throwable tr) {
					init9_logCalled = true;
					System.out.println(msg);
				}
			};
			opts.logLevel = Log.NONE;
			ably = new AblyRealtime(opts);
			assertFalse("Log handler incorrectly called", init9_logCalled);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init9: Unexpected exception instantiating library");
		} finally {
			if(ably != null) ably.close();
		}
	}
}
