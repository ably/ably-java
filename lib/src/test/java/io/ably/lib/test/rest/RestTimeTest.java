package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Helpers.AsyncWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class RestTimeTest extends ParameterizedTest {

	/**
	 * Verify accuracy of time (to within 2 seconds of actual time)
	 */
	@Test
	public void time0() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
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
			ClientOptions opts = createOptions();
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
			ClientOptions opts = createOptions();
			opts.environment = null;
			opts.restHost = "this.restHost.does.not.exist";
			AblyRest ably = new AblyRest(opts);
			ably.time();
			fail("time2: Unexpected success getting time");
		} catch (AblyException e) {
			assertEquals("time2: Unexpected error code", e.errorInfo.statusCode, 404);
		}
	}

	/**
	 * Verify accuracy with async method
	 */
	@Test
	public void time_async() {
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			final AblyRest ably = new AblyRest(opts);
			AsyncWaiter<Long> callback = new AsyncWaiter<Long>();
			ably.timeAsync(callback);
			callback.waitFor();

			if(callback.error != null) {
				fail("time_async: Unexpected error getting time");
			} else if(callback.result == null) {
				fail("time_async: No time value returned");
			} else {
				long actualTime = System.currentTimeMillis();
				assertTrue(Math.abs(actualTime - callback.result) < 2000);
			}
		} catch(AblyException e) {
			fail("time_async: Unexpected exception instancing Ably REST library");
		}
	}
}
