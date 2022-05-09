package io.ably.lib.test.rest;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.platform.PlatformBase;
import io.ably.lib.push.PushBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class RestClientTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(30);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Include `request_id` if addRequestIds in client options is enabled
     * Spec: RSC7c
     */
    @Test
    public void request_contains_request_id() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);
        Helpers.RawHttpTracker httpListener = new Helpers.RawHttpTracker();

        opts.httpListener = httpListener;
        /* disable addRequestIds */
        opts.addRequestIds = false;
        AblyBase<PushBase, PlatformBase, RestChannelBase> ablyA = createAblyRest(opts);

        ablyA.channels.get("test").publish("foo", "bar");
        /* verify client_id is not a part of url query */
        assertNull("Verify clientId is not present in query", httpListener.getFirstRequest().url.getQuery());

        /* enable addRequestIds */
        opts.addRequestIds = true;
        AblyBase<PushBase, PlatformBase, RestChannelBase> ablyB = createAblyRest(opts);

        ablyB.channels.get("test").publish("foo", "bar");
        /* verify client_id is a part of url query */
        assertTrue("Verify clientId is present in query", httpListener.getLastRequest().url.getQuery().contains("request_id"));
    }

    /**
     * Include `request_id` in ErrorInfo if addRequestIds in client options is enabled
     * Spec: RSC7c
     */
    @Test
    public void error_info_contains_request_id() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        Helpers.RawHttpTracker httpListener = new Helpers.RawHttpTracker();
        opts.httpListener = httpListener;
        opts.addRequestIds = true;
        opts.environment = null;
        opts.restHost = "";
        opts.fallbackHosts = new String[]{"ably.com"};
        AblyBase<PushBase, PlatformBase, RestChannelBase> ably = createAblyRest(opts);

        try{
            ably.channels.get("test").publish("foo", "bar");
        } catch (AblyException e) {
            assertTrue(e.errorInfo.message.contains("request_id"));
        }

        /* verify client_id is a part of url query */
        assertTrue("Verify clientId is present in query", httpListener.getFirstRequest().url.getQuery().contains("request_id"));
    }

    /**
     * `clientId` remain the same if a request is retried to a fallback host
     * Spec: RSC7c
     */
    @Test
    public void request_id_remain_same_retried_fallbacks() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        Helpers.RawHttpTracker httpListener = new Helpers.RawHttpTracker();
        opts.httpListener = httpListener;
        opts.addRequestIds = true;
        opts.environment = null;
        opts.restHost = "invalid-host1.com";
        opts.fallbackHosts = new String[]{"invalid-host2.com", "invalid-host3.com"};
        AblyBase<PushBase, PlatformBase, RestChannelBase> ably = createAblyRest(opts);

        try{
            ably.channels.get("test").publish("foo", "bar");
        } catch (AblyException e) { }

        /* verify client_id is a part of url query */
        assertTrue("Verify clientId is present in query", httpListener.getFirstRequest().url.getQuery().contains("request_id"));
        String query = httpListener.getFirstRequest().url.getQuery();
        /* verify request was retried 3 times */
        assertEquals(3, httpListener.values().size());
        for (Helpers.RawHttpRequest rawHttpRequest : httpListener.values()) {
            assertTrue("Verify clientId remain the same if a request is retried to a fallback host", rawHttpRequest.url.getQuery().contains(query));
        }
    }
}
