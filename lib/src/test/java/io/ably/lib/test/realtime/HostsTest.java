package io.ably.lib.test.realtime;

import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;
import io.ably.lib.types.ClientOptions;

import org.hamcrest.Matchers;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.ably.lib.types.ClientOptions;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class HostsTest {

	/**
	 * Tests for Hosts class (fallback hosts).
	 */
	@Test
	public void hosts_fallback() {
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
	}

	/**
	 * Expect a null, when we provide empty array of fallback hosts
	 */
	@Test
	public void hosts_fallback_empty_array() {
		ClientOptions options = new ClientOptions();
		String[] fallbackHosts = {};
		options.fallbackHosts = fallbackHosts;
		Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
		String host = hosts.getFallback(Defaults.HOST_REALTIME);
		assertThat(host, is(equalTo(null)));
	}

	/**
	 * Expect that returned host is contained within custom host list in options,
	 * but in shuffled order and the right number of them.
	 */
	@Test
	public void hosts_fallback_custom_hosts() {
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
	}

	/**
	 * Expect that returned host is contained within default host list
	 */
	@Test
	public void hosts_fallback_no_custom_hosts(){
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
	}
}
