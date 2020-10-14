package io.ably.lib.transport;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.ably.lib.types.AblyException;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;
import org.junit.Test;

import io.ably.lib.types.ClientOptions;

public class HostsTest {

    /**
     * Tests for Hosts class (fallback hosts).
     */
    @Test
    public void hosts_fallback() throws AblyException {
        ClientOptions options = new ClientOptions();
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);
        String host = hosts.getFallback(Defaults.HOST_REALTIME);
        /* Expect given fallback host string is (relatively) valid */
        assertThat(host, not(isEmptyOrNullString()));
        /* Expect multiple calls will provide different (relatively) valid fallback hosts */
        String host2 = hosts.getFallback(host);
        assertThat(host2, not(isEmptyOrNullString()));
        assertThat(host2, not(host));
        /* Expect a null, when we requested more than available fallback hosts */
        for (int i = Defaults.HOST_FALLBACKS.length - 1; i > 0; i--) {
            assertThat(host2, notNullValue());
            host2 = hosts.getFallback(host2);
        }
    }

    /**
     * Expect an exception when setting both realtimeHost and environment.
     */
    @Test(expected = AblyException.class)
    public void hosts_host_and_environment() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.environment = "myenv";
        new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
    }

    /**
     * Expect a null, when we provide empty array of fallback hosts
     */
    @Test
    public void hosts_fallback_empty_array() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.fallbackHosts = new String[]{};
        Hosts hosts = new Hosts(Defaults.HOST_REALTIME, Defaults.HOST_REALTIME, options);
        String host = hosts.getFallback(Defaults.HOST_REALTIME);
        assertThat(host, nullValue());
    }

    /**
     * Expect that returned host is contained within custom host list in options,
     * but in shuffled order and the right number of them.
     */
    @Test
    public void hosts_fallback_custom_hosts() throws AblyException {
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
        assertThat(idx, is(customHosts.length));
        assertTrue(allFound);
        assertTrue(shuffled);
    }

    /**
     * Expect that returned host is contained within default host list
     */
    @Test
    public void hosts_fallback_no_custom_hosts() throws AblyException {
        ClientOptions options = new ClientOptions();
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);
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
        assertThat(idx, is(Defaults.HOST_FALLBACKS.length));
        assertTrue(allFound);
        assertTrue(shuffled);
    }

    /**
     * Expect a null, when realtimeHost is non-default
     */
    @Test
    public void hosts_fallback_overridden_host() throws AblyException {
        ClientOptions options = new ClientOptions();
        String host = "overridden.ably.io";
        Hosts hosts = new Hosts(host, Defaults.HOST_REALTIME, options);
        host = hosts.getFallback(host);
        assertThat(host, nullValue());
    }

    /**
     * Expect that returned host is contained within default host list
     * when realtimeHost is non-default and fallbackHostsUseDefault is set
     */
    @Test
    public void hosts_fallback_use_default() throws AblyException {
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
        assertThat(idx, is(Defaults.HOST_FALLBACKS.length));
        assertTrue(allFound);
        assertTrue(shuffled);
    }

    /**
     * It is not allowed to use default fallback hosts and at the same time
     * provide custom fallback hosts.
     */
    @Test(expected = AblyException.class)
    public void hosts_fallback_use_default_and_set_fallback_hosts() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.fallbackHostsUseDefault = true;
        options.fallbackHosts = new String[] { "custom.ably-realtime.com" };

        new Hosts(null, Defaults.HOST_REALTIME, options);
    }

    /**
     * Expect that returned fallback hosts containing the environment information.
     */
    @Test
    public void hosts_fallback_use_environment() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.environment = "sandbox";
        String[] expectedEnvironmentFallbackHosts = Defaults.getEnvironmentFallbackHosts(options.environment);
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        String host = "sandbox-" + Defaults.HOST_REALTIME;
        List<String> returnedEnvironmentFallbackHosts = new ArrayList<>();
        while ((host = hosts.getFallback(host)) != null){
            returnedEnvironmentFallbackHosts.add(host);
        }

        assertThat(returnedEnvironmentFallbackHosts, containsInAnyOrder(expectedEnvironmentFallbackHosts));
    }

    /**
     * Expect that returned default fallback hosts without environment according to RSC15g4.
     */
    @Test
    public void hosts_fallback_use_default_fallback_hosts_and_environment() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.fallbackHostsUseDefault = true;
        options.environment = "sandbox";
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);
        String host = "sandbox-" + Defaults.HOST_REALTIME;
        List<String> returnedEnvironmentFallbackHosts = new ArrayList<>();

        while ((host = hosts.getFallback(host)) != null){
            returnedEnvironmentFallbackHosts.add(host);
        }

        assertThat(returnedEnvironmentFallbackHosts, containsInAnyOrder(Defaults.HOST_FALLBACKS));
    }

    @Test
    public void hosts_no_fallback_when_port_is_defined() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.port = 8080;
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        assertThat(hosts.getFallback(Defaults.HOST_REALTIME), nullValue());
    }

    @Test
    public void hosts_no_fallback_when_tlsport_is_defined() throws AblyException {
        ClientOptions options = new ClientOptions();
        options.tlsPort = 8081;
        Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, options);

        assertThat(hosts.getFallback(Defaults.HOST_REALTIME), nullValue());
    }
}
