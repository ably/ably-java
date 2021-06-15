package io.ably.lib.test.rest;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;

public class RestClientTest extends ParameterizedTest {

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
        AblyRest ablyA = new AblyRest(opts);

        ablyA.channels.get("test").publish("foo", "bar");
        /* verify client_id is not a part of url query */
        assertEquals("Verify clientId is not present in query", null, httpListener.getFirstRequest().url.getQuery());

        /* enable addRequestIds */
        opts.addRequestIds = true;
        AblyRest ablyB = new AblyRest(opts);

        ablyB.channels.get("test").publish("foo", "bar");
        /* verify client_id is a part of url query */
        assertEquals("Verify clientId is present in query", true, httpListener.getLastRequest().url.getQuery().contains("request_id"));
    }
}
