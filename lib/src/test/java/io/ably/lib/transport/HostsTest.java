package io.ably.lib.transport;

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
}