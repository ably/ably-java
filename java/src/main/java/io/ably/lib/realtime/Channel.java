package io.ably.lib.realtime;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

public class Channel extends RealtimeChannelBase {
    Channel(AblyRealtimeBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }
}
