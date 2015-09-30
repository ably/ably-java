package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

import org.junit.Test;

public class RestTimeTest {

	/**
	 * Verify accuracy of time (to within 2 seconds of actual time)
	 */
	@Test
	public void time0() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			opts.restHost = testVars.restHost;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			AblyRest ably = new AblyRest(opts);
			long reportedTime = ably.time();
			long actualTime = System.currentTimeMillis();
			assertTrue(Math.abs(actualTime - reportedTime) < 2000);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("time0: Unexpected exception getting time");
		}
	}

	/**
	 * Verify time can be obtained without any valid key or token
	 */
	@Test
	public void time1() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = testVars.restHost;
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			opts.tls = testVars.tls;
			AblyRest ablyNoAuth = new AblyRest(opts);
			ablyNoAuth.time();
		} catch (AblyException e) {
			e.printStackTrace();
			fail("time1: Unexpected exception getting time");
		}
	}

	/**
	 * Verify time fails without valid restHost
	 */
	@Test
	public void time2() {
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions();
			opts.restHost = "this.restHost.does.not.exist";
			opts.port = testVars.port;
			opts.tlsPort = testVars.tlsPort;
			AblyRest ably = new AblyRest(opts);
			ably.time();
			fail("time2: Unexpected success getting time");
		} catch (AblyException e) {
			assertEquals("time2: Unexpected error code", e.errorInfo.statusCode, 404);
		}
	}
}
