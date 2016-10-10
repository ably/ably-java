package io.ably.lib.test.realtime;

import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Created by gokhanbarisaker on 2/2/16.
 */
public class HostsTest {

    /**
     * Expect given fallback host string is (relatively) valid
     */
    @Test
    public void hosts_fallback_single() {
        String host = Hosts.getFallback(null);
        assertThat(host, is(not(isEmptyOrNullString())));
    }

    /**
     * Expect multiple calls will provide different (relatively) valid fallback hosts
     */
    @Test
    public void hosts_fallback_multiple() {
        String host = Hosts.getFallback(null);
        assertThat(Hosts.getFallback(host), is(not(allOf(isEmptyOrNullString(), equalTo(host)))));
    }

	/**
	 * Expect a null, when we requested more than available fallback hosts
     */
    @Test
    public void hosts_fallback_traverse_all() {
        String host = Hosts.getFallback(null);

        for (int i = Defaults.HOST_FALLBACKS.size(); i > 0; i--) {
            assertThat(host, is(not(equalTo(null))));
            host = Hosts.getFallback(host);
        }

        assertThat(host, is(equalTo(null)));
    }
}
