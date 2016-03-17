package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.ProxyOptions;
import io.ably.lib.types.Stats;

public class RestProxyTest {

	/**
	 * Check access to stats API via proxy with invalid host, expecting failure
	 */
	@Test
	public void proxy_simple_invalid_host() {
		try {
			/* setup client */
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			testVars.fillInOptions(opts);
			opts.proxy = new ProxyOptions() {{
				host = "not-sandbox-proxy.ably.io";
				port = 6128;
			}};
			AblyRest ably = new AblyRest(opts);

			/* attempt the call, expecting no exception */
			PaginatedResult<Stats> stats = ably.stats(null);
			fail("proxy_simple_invalid_host: call succeeded unexpectedly");
		} catch (AblyException e) {
			assertEquals("Verify expected error code", e.errorInfo.code, 80000);
		}
	}

	/**
	 * Check access to stats API via proxy with invalid port, expecting failure
	 */
	@Test
	public void proxy_simple_invalid_port() {
		try {
			/* setup client */
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			testVars.fillInOptions(opts);
			opts.proxy = new ProxyOptions() {{
				host = "sandbox-proxy.ably.io";
				port = 6127;
			}};
			AblyRest ably = new AblyRest(opts);

			/* attempt the call, expecting no exception */
			PaginatedResult<Stats> stats = ably.stats(null);
			fail("proxy_simple_invalid_port: call succeeded unexpectedly");
		} catch (AblyException e) {
			assertEquals("Verify expected error code", e.errorInfo.code, 80000);
		}
	}

	/**
	 * Check access to stats API via proxy
	 */
	@Test
	public void proxy_simple() {
		try {
			/* setup client */
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			testVars.fillInOptions(opts);
			opts.proxy = new ProxyOptions() {{
				host = "sandbox-proxy.ably.io";
				port = 6128;
			}};
			AblyRest ably = new AblyRest(opts);

			/* attempt the call, expecting no exception */
			PaginatedResult<Stats> stats = ably.stats(null);
			assertNotNull("Expected non-null stats", stats);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("proxy_simple: Unexpected exception");
			return;
		}
	}

	/**
	 * Check access to stats API via proxy with authentication
	 */
	@Test
	public void proxy_basic_auth() {
		try {
			/* setup client */
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
			testVars.fillInOptions(opts);
			opts.proxy = new ProxyOptions() {{
				host = "sandbox-proxy.ably.io";
				port = 6129;
				username = "ably";
				password = "password";
			}};
			AblyRest ably = new AblyRest(opts);
	
			/* attempt the call, expecting no exception */
			PaginatedResult<Stats> stats = ably.stats(null);
			assertNotNull("Expected non-null stats", stats);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("proxy_simple: Unexpected exception");
			return;
		}
	}
}
