package io.ably.lib.test.realtime;

import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class HostsTest {

	/**
	 * Tests for Hosts class (fallback hosts).
	 */
	@Test
	public void hosts_fallback() {
		Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME);
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
}
