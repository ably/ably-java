package io.ably.lib.test.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import io.ably.lib.platform.Platform;
import io.ably.lib.push.PushBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.test.common.PlatformSpecificIntegrationTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import org.junit.Test;

/**
 * Test for basic Channel operation not related to publish or history
 */
public abstract class RestChannelTest extends PlatformSpecificIntegrationTest {
    /**
     * Test if ably.channel.get() returns same object if the same name is given
     * Tests RTN2, RTN3, RSN3a, RSN4a
     */
    @Test
    public void channel_object_caching() throws AblyException {
        ClientOptions opts = new ClientOptions(testVars.keys[0].keyStr);
        AblyBase<PushBase, Platform, RestChannelBase> ablyRest = createAblyRest(opts);

        RestChannelBase channel1 = ablyRest.channels.get("channel_1");
        RestChannelBase channel2 = ablyRest.channels.get("channel_2");

        RestChannelBase channel1_copy = ablyRest.channels.get("channel_1");

        assertEquals("Verify channel objects are cached", channel1, channel1_copy);
        assertNotEquals("Verify channel objects are different if different names are requested", channel1, channel2);

        /* Test channel enumeration */
        assertEquals("Verify total number of channels", ablyRest.channels.size(), 2);
        assertTrue("Verify there is channel 1 in the list", ablyRest.channels.containsKey("channel_1"));
        assertTrue("Verify there is channel 2 in the list", ablyRest.channels.containsKey("channel_2"));
        for (final RestChannelBase channel : ablyRest.channels.values()) {
            assertTrue("Verify correct channels are in the hashmap",
                    channel == channel1 || channel == channel2);
        }
        ablyRest.channels.release("channel_1");
        channel1_copy = ablyRest.channels.get("channel_1");
        assertNotEquals("Verify channel is re-created after release", channel1, channel1_copy);
    }
}
