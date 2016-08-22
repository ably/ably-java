package io.ably.lib.transport;

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

/**
 * Created by gokhanbarisaker on 2/2/16.
 */
public class HostsTest {

    /**
     * Expect given fallback host string is (relatively) valid
     */
    @Test
    public void hosts_fallback_single() {
        String host = Hosts.getFallback(null, Defaults.HOST_FALLBACKS);
        assertThat(host, is(not(isEmptyOrNullString())));
    }

    /**
     * Expect multiple calls will provide different (relatively) valid fallback hosts
     */
    @Test
    public void hosts_fallback_multiple() {
        String host = Hosts.getFallback(null, Defaults.HOST_FALLBACKS);
        assertThat(Hosts.getFallback(host, Defaults.HOST_FALLBACKS), is(not(allOf(isEmptyOrNullString(), equalTo(host)))));
    }

	/**
	 * Expect a null, when we requested more than available fallback hosts
     */
    @Test
    public void hosts_fallback_traverse_all() {
        String host = Hosts.getFallback(null, Defaults.HOST_FALLBACKS);

        for (int i = Defaults.HOST_FALLBACKS.size(); i > 0; i--) {
            assertThat(host, is(not(equalTo(null))));
            host = Hosts.getFallback(host, Defaults.HOST_FALLBACKS);
        }

        assertThat(host, is(equalTo(null)));
    }

    /**
     * Expect a null, when we provide empty array of fallback hosts
     */
    @Test
    public void hosts_fallback_empty_array() {
        String host = Hosts.getFallback(null, Collections.<String>emptyList());
        assertThat(host, is(equalTo(null)));
    }

    /**
     * Expect that result list is contained within original list in randomized order and are the same size
     */
    @Test
    public void hosts_fallback_custom_hosts() {
        List<String> customHosts            = Arrays.asList("F.ably-realtime.com", "G.ably-realtime.com", "H.ably-realtime.com", "I.ably-realtime.com", "J.ably-realtime.com", "K.ably-realtime.com");
        List<String> customHostsRandomized  = new ArrayList<>(customHosts);
        List<String> resultList             = new ArrayList<>();

        Collections.shuffle(customHostsRandomized);
        String host = null;

        for(String ignored : customHostsRandomized){
            host = Hosts.getFallback(host, customHostsRandomized);
            assertThat(host, is(not(equalTo(null))));
            resultList.add(host);
        }

        assertTrue(customHosts.size() == resultList.size());

        assertThat(customHosts, IsIterableContainingInAnyOrder.containsInAnyOrder(resultList.toArray()));
    }

    /**
     * Expect that returned host is contained within custom host list in options
     */
    @Test
    public void hosts_fallback_custom_hosts_options(){
        List<String> customHosts    = Arrays.asList("F.ably-realtime.com", "G.ably-realtime.com", "H.ably-realtime.com", "I.ably-realtime.com", "J.ably-realtime.com", "K.ably-realtime.com");
        ClientOptions options       = new ClientOptions();

        options.fallbackHosts = customHosts;
        String host = Hosts.getFallback(null, options.fallbackHosts);

        assertTrue(options.fallbackHosts.contains(host));
    }

    /**
     * Expect that returned host is contained within default host list in options
     */
    @Test
    public void hosts_fallback_no_custom_hosts_options(){
        ClientOptions options = new ClientOptions();

        String host = Hosts.getFallback(null, options.fallbackHosts);

        assertTrue(Defaults.HOST_FALLBACKS.contains(host));
    }

    /**
     * Expect that returned host is null when setting an empty list in options
     */
    @Test
    public void hosts_fallback_empty_custom_hosts_options(){
        ClientOptions options = new ClientOptions();
        options.fallbackHosts = new ArrayList<>();

        String host = Hosts.getFallback(null, options.fallbackHosts);

        assertThat(host, is(equalTo(null)));
    }

    /**
     * Expect that returned host is null when setting null as fallback host list
     */
    @Test
    public void hosts_fallback_null_custom_hosts_options(){
        ClientOptions options = new ClientOptions();
        options.fallbackHosts = null;

        String host = Hosts.getFallback(null, options.fallbackHosts);

        assertThat(host, is(equalTo(null)));
    }


}