package io.ably.core.realtime;

import io.ably.core.types.AblyException;
import io.ably.core.types.ChannelOptions;

public class Channel extends RealtimeChannelBase {
    Channel(AblyRealtimeBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }
}
