package io.ably.lib.test.realtime;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Setup;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by kostiantyn on 8/9/2016.
 */

public class RealtimeChannelFailedTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Setup.getTestVars();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        Setup.clearTestVars();
    }

    @Test
    public void channel_filed_state_test(){
        AblyRealtime ablyRealtime = null;
        try {
            Setup.TestVars testVars = Setup.getTestVars();
            ClientOptions clientOptions = testVars.createOptions("not_an_app.invalid_key_id:invalid_key_value");
            ablyRealtime = new AblyRealtime(clientOptions);
            final Channel channel = ablyRealtime.channels.get("test_channel");

            channel.on(new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelState state, ErrorInfo reason) {
                    assertEquals("Verify filed state on channel ", channel.state, ChannelState.failed);
                }
            });
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ablyRealtime != null)
                ablyRealtime.close();
        }
    }
}
