package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;
import io.ably.lib.types.ClientOptions;

public class HostsTest {

	/**
	 * Tests for Hosts class (fallback hosts).
	 */
	@Test
	public void hosts_fallback() {
		try {
			ClientOptions options = new ClientOptions();
			Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
			String host = hosts.getFallback(Defaults.HOST_REALTIME);
			/* Expect given fallback host string is (relatively) valid */
			assertThat(host, is(not(isEmptyOrNullString())));
			/* Expect multiple calls will provide different (relatively) valid fallback hosts */
			String host2 = hosts.getFallback(host);
			assertThat(host2, is(not(allOf(isEmptyOrNullString(), equalTo(host)))));
			/* Expect a null, when we requested more than available fallback hosts */
			for (int i = Defaults.HOST_FALLBACKS.length - 1; i > 0; i--) {
				assertThat(host2, is(not(equalTo(null))));
				host2 = hosts.getFallback(host2);
			}
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}

	/**
	 * Expect an exception when setting both realtimeHost and environment.
	 */
	@Test
	public void hosts_host_and_environment() {
		try {
			ClientOptions options = new ClientOptions();
			options.environment = "myenv";
			Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
			fail("Expected exception from setting realtimeHost and environment");
		} catch (Exception e) {
			/* pass */
		}
	}

	/**
	 * Expect a null, when we provide empty array of fallback hosts
	 */
	@Test
	public void hosts_fallback_empty_array() {
		try {
			ClientOptions options = new ClientOptions();
			String[] fallbackHosts = {};
			options.fallbackHosts = fallbackHosts;
			Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
			String host = hosts.getFallback(Defaults.HOST_REALTIME);
			assertThat(host, is(equalTo(null)));
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}

	/**
	 * Expect that returned host is contained within custom host list in options,
	 * but in shuffled order and the right number of them.
	 */
	@Test
	public void hosts_fallback_custom_hosts() {
		try {
			ClientOptions options = new ClientOptions();
			String[] customHosts = { "F.ably-realtime.com", "G.ably-realtime.com", "H.ably-realtime.com", "I.ably-realtime.com", "J.ably-realtime.com", "K.ably-realtime.com" };
			options.fallbackHosts = customHosts;
			Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
			int idx;
			String host = Defaults.HOST_REALTIME;
			boolean shuffled = false;
			boolean allFound = true;
			for (idx = 0; ; ++idx) {
				host = hosts.getFallback(host);
				if (host == null)
					break;
				int found = Arrays.asList(customHosts).indexOf(host);
				if (found < 0)
					allFound = false;
				else if (found != idx)
					shuffled = true;
			}
			assertTrue(idx == customHosts.length);
			assertTrue(allFound);
			assertTrue(shuffled);
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}

	/**
	 * Expect that returned host is contained within default host list
	 */
	@Test
	public void hosts_fallback_no_custom_hosts(){
		try {
			ClientOptions options = new ClientOptions();
			Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
			int idx;
			String host = Defaults.HOST_REALTIME;
			boolean shuffled = false;
			boolean allFound = true;
			for (idx = 0; ; ++idx) {
				host = hosts.getFallback(host);
				if (host == null)
					break;
				int found = Arrays.asList(Defaults.HOST_FALLBACKS).indexOf(host);
				if (found < 0)
					allFound = false;
				else if (found != idx)
					shuffled = true;
			}
			assertTrue(idx == Defaults.HOST_FALLBACKS.length);
			assertTrue(allFound);
			assertTrue(shuffled);
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}

	/**
	 * Expect a null, when realtimeHost is non-default
	 */
	@Test
	public void hosts_fallback_overridden_host() {
		try {
			ClientOptions options = new ClientOptions();
			String host = "overridden.ably.io";
			Hosts hosts = new Hosts(host, Defaults.HOST_REALTIME, options);
			host = hosts.getFallback(host);
			assertThat(host, is(equalTo(null)));
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}

	/**
	 * Expect that returned host is contained within default host list
	 * when realtimeHost is non-default and fallbackHostsUseDefault is set
	 */
	@Test
	public void hosts_fallback_use_default(){
		try {
			ClientOptions options = new ClientOptions();
			options.fallbackHostsUseDefault = true;
			String host = "overridden.ably.io";
			Hosts hosts = new Hosts(host, Defaults.HOST_REALTIME, options);
			int idx;
			boolean shuffled = false;
			boolean allFound = true;
			for (idx = 0; ; ++idx) {
				host = hosts.getFallback(host);
				if (host == null)
					break;
				int found = Arrays.asList(Defaults.HOST_FALLBACKS).indexOf(host);
				if (found < 0)
					allFound = false;
				else if (found != idx)
					shuffled = true;
			}
			assertTrue(idx == Defaults.HOST_FALLBACKS.length);
			assertTrue(allFound);
			assertTrue(shuffled);
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		}
	}



}
